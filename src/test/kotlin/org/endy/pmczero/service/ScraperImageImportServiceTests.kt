package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.MediaAdder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.ScanFormat
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
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
import kotlin.test.assertNotNull
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
        service = ScraperImageImportService(scraperService, browserFetcher, locationService, msetService)

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
        whenever(browserFetcher.render(any(), anyOrNull(), any())).thenAnswer {
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

        verify(browserFetcher).render("http://example.org/galerie.html", "app-images", 7)
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
        whenever(browserFetcher.render(any(), anyOrNull(), any())).thenReturn(html)
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
