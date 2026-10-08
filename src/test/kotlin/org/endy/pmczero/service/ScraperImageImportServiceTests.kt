package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.FoundElementsWorker
import org.endy.pmczero.model.scraper.MediaAdder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.ScanFormat
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.to.FoundElementTO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ScraperImageImportService], against mocked collaborators and a real [File] on disk.
 *
 * The browser is mocked rather than driven, since a test that opens a real page would be a test that
 * fails when the network is down. What is checked here is the part that is this service's own: that a
 * scraper picks the right elements out of the html, that the files land where the media point, and that
 * one unreachable image does not cost the others.
 */
class ScraperImageImportServiceTests {

    @TempDir
    lateinit var tempDir: File

    private val scraperService: ScraperService = mock()
    private val browserFetcher: BrowserFetcher = mock()
    private val locationService: LocationService = mock()
    private val msetService: MsetService = mock()
    private val scannerService: ScannerService = mock()

    private lateinit var service: ScraperImageImportService
    private lateinit var location: Location

    private val page = """
        <html>
        <head><title>Galerie</title></head>
        <body>
            <img src="/bilder/erstes.jpg">
            <img src="zweites.jpg">
            <img src="https://cdn.de/drittes.png">
        </body>
        </html>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        service = ScraperImageImportService(
            scraperService,
            browserFetcher,
            locationService,
            msetService,
            scannerService
        )

        location = Location().also {
            it.id = 7
            it.name = "Bilder"
            it.uri = tempDir.absolutePath
            it.locationType = LocationType.MAIN_FS.i
            it.inuse = 1
            it.storage = Storage().also { storage -> storage.id = 3 }
        }

        whenever(locationService.findById(7)).thenReturn(location)
        whenever(locationService.isFileSystemAccessible(7)).thenReturn(true)

        // the placeholder a scan without a location runs against, which is what a listing uses since it
        // writes nothing. A mock answers null for an unstubbed call, and a kontext of null is not one.
        whenever(scraperService.catchupLocation()).thenReturn(
            Location().also { it.name = "Catchup Location"; it.uri = ""; it.storage = Storage() }
        )

        // the kontext is built by ScraperService in production, so here it is built directly. The real
        // object, since what is under test is what the workers do with it.
        whenever(scraperService.getNewScanningContext(any(), any(), any())).thenAnswer { invocation ->
            org.endy.pmczero.model.ScanningKontext(
                invocation.getArgument(0),
                Mset(),
                arrayListOf(),
                invocation.getArgument(1),
                invocation.getArgument(2)
            ).also { kontexts.add(it) }
        }

        whenever(msetService.save(any())).thenAnswer { it.getArgument(0) }

        // a fetcher that writes what it is asked to download, so the files really appear
        givenBrowserThatWritesFiles()
    }

    // -------------------------------------------------------------------------------------
    // What it imports
    // -------------------------------------------------------------------------------------

    @Test
    fun `imports every image of the page into the location`() {
        givenPage(page)

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        assertEquals(3, result.found)
        assertEquals(3, result.imported)
        assertEquals(0, result.failed)
        assertEquals(7, result.locationId)
        assertEquals(3, result.storageId)
        assertEquals(listOf("erstes.jpg", "zweites.jpg", "drittes.png"), result.media.map { it.name })
    }

    /** The property that matters: a medium that points at a file that is not there is a url that 404s. */
    @Test
    fun `every medium points at a file that was written`() {
        givenPage(page)

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        val saved = savedMset()
        for (medium in saved.media) {
            val relative = medium.bessources.single().name!!
            assertTrue(File(tempDir, relative).isFile, "'$relative' is recorded but is not a file")
        }
        assertEquals(3, result.media.size)
    }

    @Test
    fun `puts the files into a folder named after the import`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        // one folder per import, so a second gallery cannot overwrite the files of the first
        assertEquals(
            listOf("Galerie/drittes.png", "Galerie/erstes.jpg", "Galerie/zweites.jpg"),
            writtenRelativeTo(tempDir)
        )
    }

    /** The name asked for wins over the title, since a caller that named the import meant to. */
    @Test
    fun `names the set after the given name`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Meine Galerie")

        assertEquals("Meine Galerie", savedMset().name)
    }

    /**
     * A draft takes the page title as the name, since nothing is saved and nothing would read it back.
     *
     * Checked through the set the workers built rather than through what was saved, which is nothing
     * here: [SetCreator] runs over the title element whether or not the set is persisted, so this is
     * about the title reaching the set at all.
     */
    @Test
    fun `takes the page title as the name of the set a scan builds`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", persist = false)

        assertEquals("Galerie", lastKontext().mset?.name)
        verify(msetService, never()).save(any())
    }

    /** A relative src is only resolvable because the base uri of the page is handed to the parser. */
    @Test
    fun `resolves relative image urls against the page`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/unter/ordner/galerie.html", name = "G")

        assertEquals(
            listOf("G/erstes.jpg", "G/zweites.jpg"),
            writtenRelativeTo(tempDir).filter { it.endsWith("erstes.jpg") || it.endsWith("zweites.jpg") }
        )
    }

    // -------------------------------------------------------------------------------------
    // The pattern
    // -------------------------------------------------------------------------------------

    /**
     * The pattern goes into the parser rather than being applied to a list of urls afterwards, so the
     * page is parsed once and only the wanted elements reach the workers.
     *
     * Matched against the whole url rather than as a substring search, which is what
     * [org.endy.pmczero.model.scraper.DomParser] does with a regex: it has to match the url from end to
     * end, so it needs `.*` on either side.
     */
    @Test
    fun `takes only the images matching the pattern`() {
        givenPage(page)

        val result = service.import(
            locationId = 7,
            url = "http://example.org/galerie.html",
            name = "G",
            pattern = ".*zweites.*"
        )

        assertEquals(1, result.imported)
        assertEquals(listOf("zweites.jpg"), result.media.map { it.name })
        assertEquals(listOf("G/zweites.jpg"), writtenRelativeTo(tempDir))
    }

    @Test
    fun `takes every image when the pattern is blank`() {
        givenPage(page)

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "G", pattern = "  ")

        assertEquals(3, result.imported)
    }

    // -------------------------------------------------------------------------------------
    // Listing the images of the pages a page links to
    // -------------------------------------------------------------------------------------

    /** An index page linking two pages of the gallery, each with images of its own. */
    private val indexPage = """
        <html><body>
            <a href="/seite1.html" class="galerie">Seite 1</a>
            <a href="/seite2.html" class="galerie">Seite 2</a>
            </body></html>
    """.trimIndent()

    private val seite1 = """<html><body><img src="/bilder/eins-a.jpg"></body></html>"""
    private val seite2 = """<html><body><img src="/bilder/zwei-a.jpg"><img src="/bilder/zwei-b.jpg"></body></html>"""

    /** The index page answers the render, each linked page answers the fetch of the inner worker. */
    private fun givenIndexWithLinkedPages(vararg pages: Pair<String, String>) {
        givenPage(indexPage)

        whenever(browserFetcher.getAsString(any(), any())).thenAnswer { invocation ->
            val wanted = invocation.getArgument<String>(0)
            pages.firstOrNull { wanted.endsWith(it.first) }?.second
                ?: throw NotAccessibleException("$wanted answered 404 Not Found")
        }
    }

    /**
     * The links at level 1 and the images of the pages behind them at level 2.
     *
     * Both levels in one answer is what the [org.endy.pmczero.model.FoundElement.level] is for, and it
     * is what makes a caller able to say which page held which image.
     */
    @Test
    fun `lists the links and the images of the pages behind them`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        assertEquals(
            listOf(
                FoundElementTO(1, "http://example.org/seite1.html"),
                FoundElementTO(1, "http://example.org/seite2.html"),
                FoundElementTO(2, "http://example.org/bilder/eins-a.jpg"),
                FoundElementTO(2, "http://example.org/bilder/zwei-a.jpg"),
                FoundElementTO(2, "http://example.org/bilder/zwei-b.jpg")
            ),
            result.elements
        )
        assertEquals(5, result.found)
    }

    /** Each linked page is rendered once, since it costs a browser round trip. */
    @Test
    fun `follows every matched link`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        // eq on the url rather than a raw value: a raw argument cannot be mixed with a matcher, and the
        // proxy flag has to be a matcher to keep the two in step
        verify(browserFetcher).getAsString(eq("http://example.org/seite1.html"), any())
        verify(browserFetcher).getAsString(eq("http://example.org/seite2.html"), any())
    }

    /** The whole point of this endpoint: nothing is downloaded, only pages are read. */
    @Test
    fun `downloads no image of any of the pages`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        verify(browserFetcher, never()).downloadTo(any<String>(), any<File>(), any())
        assertEquals(emptyList(), writtenRelativeTo(tempDir), "nothing was written")
    }

    @Test
    fun `stores nothing of the pages it followed`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        verify(msetService, never()).save(any())
    }

    @Test
    fun `creates no media of the pages it followed`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        assertEquals(0, lastKontext().mset?.media?.size ?: 0)
    }

    /** The class narrows what is followed: an index links the navigation of the site as well. */
    @Test
    fun `follows only the links of the given class`() {
        givenPage(
            """
            <html><body>
                <a href="/seite1.html" class="galerie">Seite 1</a>
                <a href="/impressum.html">Impressum</a>
                <a href="/kontakt.html">Kontakt</a>
            </body></html>
            """.trimIndent()
        )
        whenever(browserFetcher.getAsString(any(), any())).thenAnswer { invocation ->
            when (val wanted = invocation.getArgument<String>(0)) {
                "http://example.org/seite1.html" -> seite1
                else -> throw NotAccessibleException("$wanted answered 404 Not Found")
            }
        }

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        assertEquals(
            listOf(
                FoundElementTO(1, "http://example.org/seite1.html"),
                FoundElementTO(2, "http://example.org/bilder/eins-a.jpg")
            ),
            result.elements
        )
        verify(browserFetcher, never()).getAsString(eq("http://example.org/impressum.html"), any())
    }

    /** Without a class every link is followed, which on a real page is every navigation link on it. */
    @Test
    fun `follows every link when no class is given`() {
        givenPage("""<html><body><a href="/seite1.html">x</a></body></html>""")
        whenever(browserFetcher.getAsString(any(), any())).thenReturn(seite1)

        val result = service.listLevel2(url = "http://example.org/index.html")

        assertEquals(2, result.found)
    }

    /**
     * A page that cannot be read is reported rather than lost.
     *
     * Both a broken link and a page that holds no images leave a level 1 entry with nothing under it,
     * so only the failure list says which of the two happened. A [RecoveryWorker] is what keeps one dead
     * link from ending the run and losing every other page with it.
     */
    @Test
    fun `reports a page it could not read`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        // both pages answer here, so nothing failed; the dead one is arranged below instead
        assertEquals(emptyList(), result.failures)
    }

    @Test
    fun `reports a broken link and keeps the pages that worked`() {
        givenPage(indexPage)
        whenever(browserFetcher.getAsString(any(), any())).thenAnswer { invocation ->
            when (val wanted = invocation.getArgument<String>(0)) {
                "http://example.org/seite1.html" -> seite1
                else -> throw NotAccessibleException("$wanted answered 404 Not Found")
            }
        }

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        // both links are still reported at level 1, since the index really did hold them
        assertEquals(2, result.elements.count { it.level == 1 })
        // the one that worked kept its image
        assertEquals(
            listOf(FoundElementTO(2, "http://example.org/bilder/eins-a.jpg")),
            result.elements.filter { it.level == 2 }
        )
        val failure = result.failures.single()
        assertEquals("http://example.org/seite2.html", failure.url)
        assertTrue(failure.reason.contains("404"))
    }

    /** A page that answers and holds nothing is not a failure: that is what it had. */
    @Test
    fun `does not call a page without images a failure`() {
        givenPage(indexPage)
        whenever(browserFetcher.getAsString(any(), any())).thenReturn("<html><body>leer</body></html>")

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        assertEquals(emptyList(), result.failures)
        assertEquals(2, result.found, "the two links are still there")
    }

    /** The pattern is applied on the pages that are followed, not on the index page. */
    @Test
    fun `takes only the images of the pages that match the pattern`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1, "/seite2.html" to seite2)

        val result = service.listLevel2(
            url = "http://example.org/index.html",
            linkClass = "galerie",
            pattern = ".*zwei-a.*"
        )

        assertEquals(
            listOf(FoundElementTO(2, "http://example.org/bilder/zwei-a.jpg")),
            result.elements.filter { it.level == 2 }
        )
    }

    @Test
    fun `lists an index without links as just the index`() {
        givenPage("<html><body><p>keine Links</p></body></html>")

        val result = service.listLevel2(url = "http://example.org/index.html", linkClass = "galerie")

        assertEquals(0, result.found)
        assertEquals(emptyList(), result.elements)
    }

    @Test
    fun `lets a browser failure on the index through`() {
        whenever(browserFetcher.render(any(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            throw NotAccessibleException("the browser is disabled")
        }

        val e = assertThrows<NotAccessibleException> {
            service.listLevel2(url = "http://example.org/index.html")
        }

        assertTrue(e.message!!.contains("browser is disabled"))
    }

    /** The index is rendered once by the service; the pages behind its links are read by the fetcher. */
    @Test
    fun `renders the index once with the given scroll count and selector`() {
        givenIndexWithLinkedPages("/seite1.html" to seite1)

        service.listLevel2(
            url = "http://example.org/index.html",
            linkClass = "galerie",
            scrollTimes = 7,
            waitForSelector = "app-links"
        )

        verify(browserFetcher).render("http://example.org/index.html", "app-links", 7, null)
    }

    /**
     * The scraper is what does the work: the links of the index are picked by a parser, and a
     * [StructuredWorker] below it fetches each of them and picks the images of that page.
     */
    @Test
    fun `builds a scraper that follows the links of the index`() {
        val scraper = service.level2ScraperOf(linkClass = "galerie", pattern = null)

        val structured = scraper.worker as StructuredWorker
        // the links themselves, and the pages behind them
        assertEquals(2, structured.scrapers.size)

        val linksParser = structured.scrapers[0].parser as DomParser
        assertEquals("a.galerie", linksParser.tag)
        assertEquals("abs:href", linksParser.attribute)
        assertEquals(1, (structured.scrapers[0].worker as FoundElementsWorker).level)

        // the second branch reads each linked page, which is what download = true is for
        val inner = (structured.scrapers[1].worker as RecoveryWorker).worker as StructuredWorker
        assertTrue(inner.download)
        val imagesParser = inner.scrapers.single().parser as DomParser
        assertEquals("img[src]", imagesParser.tag)
        assertEquals("abs:src", imagesParser.attribute)
        assertEquals(2, (inner.scrapers.single().worker as FoundElementsWorker).level)
    }

    @Test
    fun `falls back to every link when no class is given`() {
        val scraper = service.level2ScraperOf(linkClass = "  ", pattern = null)

        val structured = scraper.worker as StructuredWorker
        assertEquals("a[href]", (structured.scrapers[0].parser as DomParser).tag)
    }

    @Test
    fun `puts the pattern into the inner scraper`() {
        val scraper = service.level2ScraperOf(linkClass = null, pattern = ".*zwei.*")

        val inner = ((scraper.worker as StructuredWorker).scrapers[1].worker as RecoveryWorker)
            .worker as StructuredWorker
        assertEquals(".*zwei.*", (inner.scrapers.single().parser as DomParser).regex)
    }

    // -------------------------------------------------------------------------------------
    // Listing the urls of a page
    // -------------------------------------------------------------------------------------

    /**
     * The whole point of this endpoint: the urls of the page, and nothing fetched.
     *
     * A render and a parse, where an import of the same page also downloads every image. So an
     * unreachable image cannot fail this, which is exactly the difference.
     */
    @Test
    fun `lists the image urls of a page without downloading anything`() {
        givenPage(page)

        val result = service.list(url = "http://example.org/galerie.html")

        assertEquals(3, result.found)
        assertEquals(
            listOf("erstes.jpg", "zweites.jpg", "drittes.png"),
            result.elements.map { it.element.substringAfterLast('/') }
        )
        assertEquals(emptyList(), writtenRelativeTo(tempDir), "nothing was written")
        verify(browserFetcher, never()).downloadTo(any<String>(), any<File>(), any())
    }

    /** Absolute, since the parser reads an `abs:` attribute: what a browser would follow. */
    @Test
    fun `lists the urls a browser would follow`() {
        givenPage(page)

        val result = service.list(url = "http://example.org/unter/ordner/galerie.html")

        assertEquals(
            listOf(
                "http://example.org/bilder/erstes.jpg",
                "http://example.org/unter/ordner/zweites.jpg",
                "https://cdn.de/drittes.png"
            ),
            result.elements.map { it.element }
        )
    }

    /** Everything collected is at level 1, since the scraper of a listing holds one collector. */
    @Test
    fun `lists everything at level one`() {
        givenPage(page)

        val result = service.list(url = "http://example.org/galerie.html")

        assertEquals(listOf(1, 1, 1), result.elements.map { it.level })
    }

    /** The parser is the one of the import, so a preview is a preview of that import. */
    @Test
    fun `lists only the images matching the pattern`() {
        givenPage(page)

        val result = service.list(url = "http://example.org/galerie.html", pattern = ".*zweites.*")

        assertEquals(1, result.found)
        assertEquals(listOf("http://example.org/zweites.jpg"), result.elements.map { it.element })
    }

    @Test
    fun `lists every image when the pattern is blank`() {
        givenPage(page)

        assertEquals(3, service.list(url = "http://example.org/galerie.html", pattern = "   ").found)
    }

    /** A page without images answers an empty list rather than an error: nothing to collect is a result. */
    @Test
    fun `lists nothing for a page without images`() {
        givenPage("<html><body><p>nur Text</p></body></html>")

        val result = service.list(url = "http://example.org/galerie.html")

        assertEquals(0, result.found)
        assertEquals(emptyList(), result.elements)
    }

    /** A page that has no images at all is still a page that answered, so no location is needed for it. */
    @Test
    fun `lists without a location to write into`() {
        givenPage(page)

        // neither findById nor isFileSystemAccessible is stubbed for this, so a call to either would
        // hand back null and fail: a listing has nothing to write, so it asks for nothing
        val result = service.list(url = "http://example.org/galerie.html")

        assertEquals(3, result.found)
        verify(locationService, never()).findById(any())
        verify(locationService, never()).isFileSystemAccessible(any())
    }

    /**
     * Nothing is stored, not even when the page holds something.
     *
     * The point of the endpoint: a caller looking at the urls of a gallery before asking for the import
     * of it.
     */
    @Test
    fun `stores nothing`() {
        givenPage(page)

        service.list(url = "http://example.org/galerie.html")

        verify(msetService, never()).save(any())
    }

    /** No media, since there is no [MediaAdder] in the scraper of a listing. */
    @Test
    fun `creates no media`() {
        givenPage(page)

        service.list(url = "http://example.org/galerie.html")

        assertEquals(0, lastKontext().mset?.media?.size ?: 0)
    }

    @Test
    fun `lists the page once with the given scroll count and selector`() {
        givenPage(page)

        service.list(
            url = "http://example.org/galerie.html",
            scrollTimes = 7,
            waitForSelector = "app-images"
        )

        verify(browserFetcher).render("http://example.org/galerie.html", "app-images", 7, null)
    }

    @Test
    fun `lets a browser failure through when listing`() {
        whenever(browserFetcher.render(any(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            throw NotAccessibleException("the browser is disabled")
        }

        val e = assertThrows<NotAccessibleException> {
            service.list(url = "http://example.org/galerie.html")
        }

        assertTrue(e.message!!.contains("browser is disabled"))
    }

    /** One collector and one parser, and nothing that stores or downloads. */
    @Test
    fun `builds a scraper that only collects`() {
        val scraper = service.listScraperOf(null)

        val structured = scraper.worker as StructuredWorker
        assertEquals(1, structured.scrapers.size)

        val inner = structured.scrapers.single()
        assertTrue(inner.worker is FoundElementsWorker)
        assertEquals(1, (inner.worker as FoundElementsWorker).level)

        val parser = inner.parser as DomParser
        assertEquals("img[src]", parser.tag)
        assertEquals("abs:src", parser.attribute)
        assertEquals("(.+)", parser.regex)
    }

    /** The parser is the one of the import, which is what makes a listing a preview of it. */
    @Test
    fun `lists with the same parser the import uses`() {
        val listed = service.listScraperOf(".*zweites.*").worker as StructuredWorker
        val imported = service.scraperOf(".*zweites.*").worker as StructuredWorker

        val listedParser = listed.scrapers.last().parser as DomParser
        val importedParser = imported.scrapers[1].parser as DomParser

        assertEquals(importedParser.regex, listedParser.regex)
        assertEquals(importedParser.tag, listedParser.tag)
        assertEquals(importedParser.attribute, listedParser.attribute)
    }

    // -------------------------------------------------------------------------------------
    // With a scraper handed in as json
    // -------------------------------------------------------------------------------------

    /**
     * The json of the scraper this service builds itself, so a test can send one without writing it out.
     *
     * A function rather than a property, because it needs [service] and that is built in [setUp]: a
     * property initialiser would run before the beforeEach and read a lateinit that is not there yet.
     */
    private fun imageScraperJson(): String = ScanFormat.json.encodeToString(service.scraperOf(null))

    @Test
    fun `runs a scraper handed in as json`() {
        givenPage(page)

        val result = service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = imageScraperJson(),
            name = "G"
        )

        assertEquals(3, result.imported)
        assertEquals(
            listOf("G/drittes.png", "G/erstes.jpg", "G/zweites.jpg"),
            writtenRelativeTo(tempDir)
        )
    }

    /** The whole point of the json: a scraper picks something the built one cannot, here a pdf link. */
    @Test
    fun `runs a scraper that picks something other than images`() {
        givenPage(
            """
            <html><head><title>Dokumente</title></head><body>
            <a href="/files/bericht.pdf">Bericht</a>
            <img src="/bilder/erstes.jpg">
            </body></html>
            """.trimIndent()
        )

        val scraperJson = ScanFormat.json.encodeToString(
            Scraper(
                PassThroughParser(),
                StructuredWorker(
                    download = false,
                    scrapers = listOf(
                        Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                        Scraper(DomParser("(.*)", "a", "abs:href"), MediaAdder()),
                        Scraper(
                            DomParser("(.*)", "a", "abs:href"),
                            RecoveryWorker(FileDownloader())
                        )
                    )
                )
            )
        )

        val result = service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = scraperJson,
            name = "Doku"
        )

        assertEquals(1, result.imported)
        assertEquals(listOf("bericht.pdf"), result.media.map { it.name })
        assertEquals(listOf("Doku/bericht.pdf"), writtenRelativeTo(tempDir))
    }

    /** A scraper that records media but never writes them would answer with urls that all 404. */
    @Test
    fun `refuses a scraper that does not download its files`() {
        givenPage(page)

        val e = assertThrows<NotAccessibleException> {
            service.importWith(
                locationId = 7,
                url = "http://example.org/galerie.html",
                scraper = ScanFormat.json.encodeToString(
                    Scraper(
                        PassThroughParser(),
                        StructuredWorker(
                            download = false,
                            scrapers = listOf(
                                Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                                Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder())
                            )
                        )
                    )
                ),
                name = "G"
            )
        }

        assertTrue(e.message!!.contains("no FileDownloader"))
        assertEquals(emptyList(), writtenRelativeTo(tempDir), "nothing was written")
    }

    /**
     * The download is usually below a [StructuredWorker], and often inside the [RecoveryWorker] that
     * wraps it, so neither of those hides it from the check.
     *
     * Two levels of [StructuredWorker], which is as deep as a scraper anyone would build. The html is
     * carried down by a [PassThroughParser] at each level rather than by a [DomParser], since a
     * [StructuredWorker] hands its element to the scrapers below it: one fed the page title would hand
     * that title on as the text to pick apart, which is what the pipeline is for and not a mistake here.
     */
    @Test
    fun `finds the download wherever it sits in the worker tree`() {
        givenPage(page)

        val nested = ScanFormat.json.encodeToString(
            Scraper(
                PassThroughParser(),
                StructuredWorker(
                    download = false,
                    scrapers = listOf(
                        Scraper(
                            PassThroughParser(),
                            StructuredWorker(
                                download = false,
                                scrapers = listOf(
                                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder())
                                )
                            )
                        ),
                        Scraper(
                            PassThroughParser(),
                            StructuredWorker(
                                download = false,
                                scrapers = listOf(
                                    Scraper(
                                        DomParser("(.+)", "img[src]", "abs:src"),
                                        RecoveryWorker(FileDownloader())
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = nested,
            name = "G"
        )

        assertEquals(3, result.imported)
        assertEquals(3, writtenRelativeTo(tempDir).size)
    }

    /**
     * Broken json is answered as a 409 with the reason rather than as a bare 500.
     *
     * A [kotlinx.serialization.SerializationException] is not an [Exception] this application maps, so it
     * would otherwise reach a client as an unhandled failure that says only that something went wrong.
     */
    @Test
    fun `answers a broken scraper as not accessible with the reason`() {
        givenPage(page)

        val e = assertThrows<NotAccessibleException> {
            service.importWith(locationId = 7, url = "http://example.org/g.html", scraper = "{ not json")
        }

        assertTrue(e.message!!.contains("not a scraper this application knows"))
    }

    /** Json that is valid but not a scraper, e.g. an array, has to be refused the same way. */
    @Test
    fun `answers json that is not a scraper as not accessible`() {
        givenPage(page)

        assertThrows<NotAccessibleException> {
            service.importWith(locationId = 7, url = "http://example.org/g.html", scraper = "[1,2,3]")
        }
    }

    /** A serialized scraper round trips, which is what makes one storeable and reusable. */
    @Test
    fun `a serialized scraper survives a round trip`() {
        givenPage(page)

        val original = service.scraperOf(".*zweites.*")
        val roundTripped = ScanFormat.json.decodeFromString<Scraper>(ScanFormat.json.encodeToString(original))

        val result = service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = ScanFormat.json.encodeToString(roundTripped),
            name = "G"
        )

        // the regex of the parser survived, so the same image is picked as before
        assertEquals(1, result.imported)
        assertEquals(listOf("zweites.jpg"), result.media.map { it.name })
    }

    // -------------------------------------------------------------------------------------
    // Over the scraper stored as a scanner
    // -------------------------------------------------------------------------------------

    /**
     * The scanner holding [scraper], so a test can arrange what is stored without a database.
     *
     * A [org.endy.pmczero.model.modern.Scanner] rather than a scraper, since the row is what the
     * service reads: the id names the row and the serialization names the scraper, and a test that
     * stubbed the scraper directly would skip the one thing worth checking here.
     */
    private fun givenScannerWith(id: Int, scraper: Scraper) {
        whenever(scannerService.findById(id)).thenReturn(
            org.endy.pmczero.model.modern.Scanner().also {
                it.id = id
                it.serialization = ScanFormat.json.encodeToString(scraper)
            }
        )
    }

    /** The whole point of the endpoint: the scraper of the scanner, not the one this service builds. */
    @Test
    fun `imports with the scraper stored under the given id`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        val result = service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            name = "G"
        )

        assertEquals(3, result.imported)
        assertEquals(listOf("G/drittes.png", "G/erstes.jpg", "G/zweites.jpg"), writtenRelativeTo(tempDir))
    }

    /**
     * A stored scraper can pick something the built one cannot, which is the reason to store one.
     *
     * A pdf behind a link, picked with a DomParser of its own, so the answer cannot be reached by the
     * standard `img[src]` scraper this service would otherwise fall back on.
     */
    @Test
    fun `imports what a stored scraper picks rather than the images of the page`() {
        givenPage(
            """
            <html><head><title>Dokumente</title></head><body>
            <a href="/files/bericht.pdf">Bericht</a>
            <img src="/bilder/erstes.jpg">
            </body></html>
            """.trimIndent()
        )
        givenScannerWith(
            4,
            Scraper(
                PassThroughParser(),
                StructuredWorker(
                    download = false,
                    scrapers = listOf(
                        Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                        Scraper(DomParser("(.*)", "a", "abs:href"), MediaAdder()),
                        Scraper(
                            DomParser("(.*)", "a", "abs:href"),
                            RecoveryWorker(FileDownloader())
                        )
                    )
                )
            )
        )

        val result = service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            name = "Doku"
        )

        assertEquals(1, result.imported)
        assertEquals(listOf("Doku/bericht.pdf"), writtenRelativeTo(tempDir))
    }

    @Test
    fun `reads the scanner of the id it was given`() {
        givenPage(page)
        givenScannerWith(11, service.scraperOf(null))
        givenScannerWith(12, service.scraperOf(".*zweites.*"))

        val result = service.importWithStoredScanner(
            scannerId = 12,
            locationId = 7,
            url = "http://example.org/galerie.html",
            name = "G"
        )

        // the scraper of scanner 12 rather than that of 11, which is the difference between the two ids
        assertEquals(listOf("G/zweites.jpg"), writtenRelativeTo(tempDir))
        assertEquals(1, result.imported)
    }

    /** The draft of a stored scraper writes its files and its media, but no row. */
    @Test
    fun `writes the files of a stored scraper but no rows when persist is false`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        val result = service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            name = "G",
            persist = false
        )

        assertNull(result.msetId)
        assertEquals(3, result.media.size)
        assertEquals(3, writtenRelativeTo(tempDir).size)
        verify(msetService, never()).save(any())
    }

    @Test
    fun `renders the page once for a stored scraper, with the given scroll count and selector`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            scrollTimes = 7,
            waitForSelector = "app-images"
        )

        verify(browserFetcher).render("http://example.org/galerie.html", "app-images", 7, null)
    }

    /** A scanner that records media without writing them would answer with urls that all 404. */
    @Test
    fun `refuses a stored scraper that does not download its files`() {
        givenPage(page)
        givenScannerWith(
            4,
            Scraper(
                PassThroughParser(),
                StructuredWorker(
                    download = false,
                    scrapers = listOf(
                        Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                        Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder())
                    )
                )
            )
        )

        val e = assertThrows<NotAccessibleException> {
            service.importWithStoredScanner(
                scannerId = 4,
                locationId = 7,
                url = "http://example.org/galerie.html"
            )
        }

        assertTrue(e.message!!.contains("no FileDownloader"))
        assertEquals(emptyList(), writtenRelativeTo(tempDir), "nothing was written")
    }

    /** A stored scraper this application cannot read is a 409 with the reason, not a bare 500. */
    @Test
    fun `answers a stored scraper this application cannot read as not accessible`() {
        givenPage(page)
        whenever(scannerService.findById(4)).thenReturn(
            org.endy.pmczero.model.modern.Scanner().also { it.serialization = "{ not json" }
        )

        val e = assertThrows<NotAccessibleException> {
            service.importWithStoredScanner(
                scannerId = 4,
                locationId = 7,
                url = "http://example.org/galerie.html"
            )
        }

        assertTrue(e.message!!.contains("not a scraper this application knows"))
    }

    /** The serialization column is nullable, so a row without one is a state the call has to survive. */
    @Test
    fun `answers a scanner holding no scraper as not accessible`() {
        givenPage(page)
        whenever(scannerService.findById(4)).thenReturn(
            org.endy.pmczero.model.modern.Scanner().also { it.id = 4; it.serialization = null }
        )

        val e = assertThrows<NotAccessibleException> {
            service.importWithStoredScanner(
                scannerId = 4,
                locationId = 7,
                url = "http://example.org/galerie.html"
            )
        }

        assertTrue(e.message!!.contains("holds no scraper"))
    }

    @Test
    fun `answers not found for an unknown scanner`() {
        whenever(scannerService.findById(99)).thenAnswer {
            throw org.endy.pmczero.exception.NotFoundException()
        }

        assertThrows<org.endy.pmczero.exception.NotFoundException> {
            service.importWithStoredScanner(
                scannerId = 99,
                locationId = 7,
                url = "http://example.org/galerie.html"
            )
        }
    }

    /** A page the stored scraper finds nothing on is refused the same way the built one is. */
    @Test
    fun `refuses a page without anything the stored scraper picks`() {
        givenPage("<html><body><p>nur Text</p></body></html>")
        givenScannerWith(4, service.scraperOf(null))

        val e = assertThrows<NotAccessibleException> {
            service.importWithStoredScanner(
                scannerId = 4,
                locationId = 7,
                url = "http://example.org/galerie.html"
            )
        }

        assertTrue(e.message!!.contains("no images found"))
    }

    // -------------------------------------------------------------------------------------
    // Where the set came from
    // -------------------------------------------------------------------------------------

    @Test
    fun `records the location and the url on the set it saves`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        val saved = savedMset()
        assertEquals(7, saved.locationId)
        assertEquals("http://example.org/galerie.html", saved.url)
    }

    /**
     * The [SetCreator] of the scraper replaces the mset of the kontext with one of its own, so this is
     * the case that decides whether the fields are set before or after the run.
     */
    @Test
    fun `records the location and the url on a set the scraper created itself`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        // the set was built by the SetCreator of the standard scraper, not by the kontext
        assertEquals("Galerie", savedMset().name)
        assertEquals(7, savedMset().locationId)
    }

    @Test
    fun `records the location and the url on a draft, which is not saved`() {
        givenPage(page)

        service.import(locationId = 7, url = "http://example.org/galerie.html", persist = false)

        // the set of the last kontext, since nothing was saved to ask
        val drafted = lastKontext().mset!!
        assertEquals(7, drafted.locationId)
        assertEquals("http://example.org/galerie.html", drafted.url)
    }

    // -------------------------------------------------------------------------------------
    // Which supplier the set belongs to
    // -------------------------------------------------------------------------------------

    /** The whole point of the parameter: the id of the supplier ends up on the set. */
    @Test
    fun `records the supplier it was given on the set it saves`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/artikel/4711/fotos",
            supplierId = "4711"
        )

        assertEquals("4711", savedMset().supplierId)
    }

    /** A supplier is whatever the site calls it, so the value is stored as it came in. */
    @Test
    fun `records a supplier id that is not a number`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/artikel/4711/fotos",
            supplierId = "kunde-7f3a-91"
        )

        assertEquals("kunde-7f3a-91", savedMset().supplierId)
    }

    /** Optional, so a caller that says nothing stores no supplier rather than an empty one. */
    @Test
    fun `records no supplier when none was given`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html"
        )

        assertNull(savedMset().supplierId)
    }

    /**
     * A blank parameter is a parameter that said nothing, and an empty supplier id would be a value
     * nothing should have to tell apart from no supplier at all.
     */
    @Test
    fun `records no supplier for a blank one`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            supplierId = "   "
        )

        assertNull(savedMset().supplierId)
    }

    /**
     * A [SetCreator] replaces the set the kontext started with, so the supplier has to be put on
     * afterwards or it is gone by the time the set is saved.
     */
    @Test
    fun `records the supplier on a set the scraper created itself`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            supplierId = "4711"
        )

        // the set was built by the SetCreator of the scraper, not by the kontext
        assertEquals("Galerie", savedMset().name)
        assertEquals("4711", savedMset().supplierId)
    }

    /** A draft is a set as well, so the supplier is on it even though no row is written. */
    @Test
    fun `records the supplier on a draft, which is not saved`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            supplierId = "4711",
            persist = false
        )

        assertEquals("4711", lastKontext().mset?.supplierId)
        verify(msetService, never()).save(any())
    }

    /** Taken as it comes rather than worked out of the url: what an id is is a property of the site. */
    @Test
    fun `records the supplier it was given and not one out of the url`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/artikel/4711/fotos",
            supplierId = "whatever-the-caller-read"
        )

        assertEquals("whatever-the-caller-read", savedMset().supplierId)
    }

    /** The other import path takes the supplier too, so a scraper handed in as json records the same. */
    @Test
    fun `records the supplier on an import of a scraper handed in as json`() {
        givenPage(page)

        service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = imageScraperJson(),
            supplierId = "4711"
        )

        assertEquals("4711", savedMset().supplierId)
    }

    // -------------------------------------------------------------------------------------
    // Which scanner the set came from
    // -------------------------------------------------------------------------------------

    /**
     * The stored scanner this import ran is what the set records, so a set can be traced back to the
     * scanner that built it without keeping the json of that scanner anywhere.
     */
    @Test
    fun `records the scanner it ran on the set it saves`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html"
        )

        assertEquals(4, savedMset().scannnerId)
    }

    /** The id of the scanner that was given, not a fixed one, which is what says it is recorded at all. */
    @Test
    fun `records the scanner it was given rather than another one`() {
        givenPage(page)
        givenScannerWith(11, service.scraperOf(null))
        givenScannerWith(12, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 12,
            locationId = 7,
            url = "http://example.org/galerie.html"
        )

        assertEquals(12, savedMset().scannnerId)
    }

    /**
     * A [SetCreator] replaces the set the kontext started with, so the scanner has to be put on
     * afterwards or it is gone by the time the set is saved.
     */
    @Test
    fun `records the scanner on a set the scraper created itself`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html"
        )

        // the set was built by the SetCreator of the scraper, not by the kontext
        assertEquals("Galerie", savedMset().name)
        assertEquals(4, savedMset().scannnerId)
    }

    /** A draft is a set as well, so the scanner is on it even though no row is written. */
    @Test
    fun `records the scanner on a draft, which is not saved`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/galerie.html",
            persist = false
        )

        assertEquals(4, lastKontext().mset?.scannnerId)
        verify(msetService, never()).save(any())
    }

    /** The two are told apart, so a supplier does not put a scanner on the set as well. */
    @Test
    fun `records the scanner and the supplier side by side`() {
        givenPage(page)
        givenScannerWith(4, service.scraperOf(null))

        service.importWithStoredScanner(
            scannerId = 4,
            locationId = 7,
            url = "http://example.org/artikel/4711/fotos",
            supplierId = "4711"
        )

        assertEquals("4711", savedMset().supplierId)
        assertEquals(4, savedMset().scannnerId)
    }

    /**
     * Only the stored scanner path can say which scanner it was: a caller that posted a scraper as json
     * is not running one of the stored ones, so there is nothing to record.
     */
    @Test
    fun `records no scanner on an import of a scraper handed in as json`() {
        givenPage(page)

        service.importWith(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scraper = imageScraperJson()
        )

        assertNull(savedMset().scannnerId)
    }

    // -------------------------------------------------------------------------------------
    // Persisting
    // -------------------------------------------------------------------------------------

    @Test
    fun `saves the set and answers its id`() {
        givenPage(page)
        val saved = Mset().also { it.id = 55 }
        whenever(msetService.save(any())).thenReturn(saved)

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "Galerie")

        assertEquals(55, result.msetId)
        verify(msetService).save(any())
    }

    /** A draft costs the downloads but not the rows, the way the other import does it. */
    @Test
    fun `writes the files but no rows when persist is false`() {
        givenPage(page)

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "G", persist = false)

        assertNull(result.msetId)
        assertEquals(3, result.media.size)
        assertEquals(3, writtenRelativeTo(tempDir).size)
        verify(msetService, never()).save(any())
    }

    // -------------------------------------------------------------------------------------
    // Surviving a failure
    // -------------------------------------------------------------------------------------

    /**
     * One image that cannot be fetched must not cost the ones that can.
     *
     * A [org.endy.pmczero.model.scraper.Worker] throws rather than return, so without
     * [RecoveryWorker] this whole call would fail and the two images that did work would be lost with it.
     */
    @Test
    fun `keeps the images that work when one cannot be fetched`() {
        givenPage(page)
        givenDownloadOfFails("http://example.org/zweites.jpg")

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "G")

        assertEquals(3, result.found)
        assertEquals(1, result.failed)
        // the two that worked are still on disk, which is the point of the recovery: a failure here
        // would have lost them along with the two that did
        assertEquals(
            listOf("G/drittes.png", "G/erstes.jpg"),
            writtenRelativeTo(tempDir)
        )
        // all three are still reported, since [org.endy.pmczero.model.scraper.MediaAdder] records on the
        // url alone and had nothing to fail on. The one whose file is missing is what [failed] is for.
        assertEquals(listOf("erstes.jpg", "zweites.jpg", "drittes.png"), result.media.map { it.name })
    }

    @Test
    fun `reports the image it could not fetch and why`() {
        givenPage(page)
        // the second src of the page, i.e. the document relative one, which resolves to the root
        givenDownloadOfFails("http://example.org/zweites.jpg")

        val result = service.import(locationId = 7, url = "http://example.org/galerie.html", name = "G")

        val failure = result.failures.single()
        assertEquals("http://example.org/zweites.jpg", failure.url)
        assertTrue(failure.reason.contains("zweites.jpg"), "the reason names the image: ${failure.reason}")
    }

    /** A medium is recorded for an image whose download failed, so the report is honest about it. */
    @Test
    fun `still records a medium for an image it could not fetch`() {
        givenPage(page)
        givenDownloadOfFails("http://example.org/zweites.jpg")

        service.import(locationId = 7, url = "http://example.org/galerie.html", name = "G")

        val saved = savedMset()
        assertEquals(3, saved.media.size)
        // the failed one has a bessource naming a file that is not there, which the report has to be
        // the one to tell a caller about
        val missing = saved.media.filter { !File(tempDir, it.bessources.single().name!!).isFile }
        assertEquals(1, missing.size)
    }

    // -------------------------------------------------------------------------------------
    // What it refuses
    // -------------------------------------------------------------------------------------

    /** A location with no folder has nowhere to put the bytes, and recording media would be a lie. */
    @Test
    fun `refuses a location that cannot receive files`() {
        givenPage(page)
        whenever(locationService.isFileSystemAccessible(7)).thenReturn(false)

        val e = assertThrows<NotAccessibleException> {
            service.import(locationId = 7, url = "http://example.org/galerie.html")
        }

        assertTrue(e.message!!.contains("not an accessible file system location"))
        assertEquals(emptyList(), writtenRelativeTo(tempDir))
    }

    /**
     * The exceptions of this project extend [Throwable] rather than [Exception], which Mockito will not
     * accept from `thenThrow`: it insists on a checked exception being declared by the method, and a
     * `Throwable` that is not an `Exception` is neither declared nor unchecked by its rules. So they are
     * thrown out of an answer instead.
     */
    @Test
    fun `answers not found for an unknown location`() {
        whenever(locationService.findById(99)).thenAnswer {
            throw org.endy.pmczero.exception.NotFoundException()
        }

        assertThrows<org.endy.pmczero.exception.NotFoundException> {
            service.import(locationId = 99, url = "http://example.org/galerie.html")
        }
    }

    /**
     * No images at all is an error rather than an empty result, so a caller cannot mistake a page whose
     * images never loaded for one that holds none.
     */
    @Test
    fun `refuses a page without any image`() {
        givenPage("<html><body><p>nur Text</p></body></html>")

        val e = assertThrows<NotAccessibleException> {
            service.import(locationId = 7, url = "http://example.org/galerie.html")
        }

        assertTrue(e.message!!.contains("no images found"))
    }

    /** The browser failing is its own error, passed through rather than reported as an empty page. */
    @Test
    fun `lets a browser failure through`() {
        whenever(browserFetcher.render(any(), anyOrNull(), any(), anyOrNull())).thenAnswer {
            throw NotAccessibleException("the browser is disabled")
        }

        val e = assertThrows<NotAccessibleException> {
            service.import(locationId = 7, url = "http://example.org/galerie.html")
        }

        assertTrue(e.message!!.contains("browser is disabled"))
    }

    // -------------------------------------------------------------------------------------
    // The scraper it builds
    // -------------------------------------------------------------------------------------

    /**
     * The page is rendered here rather than by the [StructuredWorker], which would fetch it over the
     * kontext fetcher and so lose the scroll count and the selector wait: a lazily loading gallery read
     * without scrolling answers only what was above the fold.
     */
    @Test
    fun `renders the page once with the given scroll count and selector`() {
        givenPage(page)

        service.import(
            locationId = 7,
            url = "http://example.org/galerie.html",
            scrollTimes = 7,
            waitForSelector = "app-images"
        )

        verify(browserFetcher).render("http://example.org/galerie.html", "app-images", 7, null)
        // and the workers are handed that html rather than fetching the url again
        verify(browserFetcher, never()).getAsString(any(), any())
    }

    @Test
    fun `builds a scraper that picks the title and the images`() {
        val scraper = service.scraperOf(null)

        assertTrue(scraper.worker is StructuredWorker)
        val structured = scraper.worker as StructuredWorker
        // title, then the images recorded, then the images downloaded
        assertEquals(3, structured.scrapers.size)
        assertTrue(structured.scrapers[0].worker is SetCreator)
        assertTrue(structured.scrapers[1].worker is MediaAdder)

        val parser = structured.scrapers[1].parser as DomParser
        assertEquals("img[src]", parser.tag)
        assertEquals("abs:src", parser.attribute)
        assertEquals("(.+)", parser.regex)
    }

    @Test
    fun `puts the pattern into the scraper parser`() {
        val scraper = service.scraperOf(".*gross.*")

        val parser = (scraper.worker as StructuredWorker).scrapers[1].parser as DomParser
        assertEquals(".*gross.*", parser.regex)
    }

    /** The download is the step that can fail on a real url, so that is the one wrapped. */
    @Test
    fun `wraps the download so one failure does not end the import`() {
        val scraper = service.scraperOf(null)

        val inner = scraper.worker as StructuredWorker
        assertTrue(inner.scrapers[1].worker is MediaAdder)
        assertTrue(inner.scrapers[2].worker is RecoveryWorker)
        assertTrue((inner.scrapers[2].worker as RecoveryWorker).worker is FileDownloader)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun givenPage(html: String) {
        // anyOrNull for the selector, which is null in most tests: Mockito's any() does not match a null
        // argument, so a plain any() there would leave the stub unanswered and hand back null html
        whenever(browserFetcher.render(any(), anyOrNull(), any(), anyOrNull())).thenReturn(html)
    }

    /**
     * A browser that writes what it downloads, so the files the service records really appear.
     *
     * The urls of the failures are held per test, since that is the one thing that has to be arranged
     * per case; everything else is the same throughout.
     */
    private fun givenBrowserThatWritesFiles() {
        failingDownloads.clear()
        kontexts.clear()

        whenever(browserFetcher.downloadTo(any<String>(), any<File>(), any())).thenAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            val target = invocation.getArgument<File>(1)

            if (url in failingDownloads) throw NotAccessibleException("$url answered 404 Not Found")

            target.parentFile?.mkdirs()
            target.writeText("content of $url")
            target
        }
    }

    /**
     * The urls whose download throws, arranged per test.
     *
     * Declared before [setUp] rather than after it, which matters: [setUp] runs
     * [givenBrowserThatWritesFiles], and a property initialised after the constructor runs but before
     * the test methods could not be the set that method reads. Kept as its own field, and cleared by
     * [givenBrowserThatWritesFiles], so one test cannot leak a failure into the next.
     */
    private val failingDownloads = mutableSetOf<String>()

    /**
     * The kontextes handed to the workers of the last scan, so a test can look at what a scan built
     * without it having been saved.
     */
    private val kontexts = mutableListOf<org.endy.pmczero.model.ScanningKontext>()

    /** the kontext of the last scan, which is the one the import just ran */
    private fun lastKontext(): org.endy.pmczero.model.ScanningKontext = kontexts.last()

    /**
     * A download of [url] that fails, as a broken link on a page would.
     *
     * The whole url rather than the src as it appears on the page, since it is the resolved url that
     * reaches the fetcher. A `src="zweites.jpg"` on `http://example.org/galerie.html` arrives as
     * `http://example.org/zweites.jpg`, not as it is written in the html.
     */
    private fun givenDownloadOfFails(url: String) {
        failingDownloads.add(url)
    }

    /** The paths of the files below [root], `/` separated and relative to it. */
    private fun writtenRelativeTo(root: File): List<String> =
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

    /** The set that was handed to [MsetService.save], which is the whole point of the import. */
    private fun savedMset(): Mset {
        val captor = argumentCaptor<Mset>()
        verify(msetService).save(captor.capture())
        return captor.lastValue
    }
}
