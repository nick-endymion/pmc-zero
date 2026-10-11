package org.endy.pmczero.service

import org.endy.pmczero.model.FoundElement
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Scanner
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.FoundElementsWorker
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.SequenceWorker
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.repository.SerializedScannerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the scanners [ScannerInitializer] builds.
 *
 * What matters is not that a scraper serialized but that it still does the thing it was built for, so
 * each scraper that picks something out of a page is run over a snippet of that page and checked
 * against what it was supposed to find. A scraper that round trips and finds nothing is a stored
 * scanner that looks fine in a list and answers nothing at all.
 *
 * The repository is mocked and the [ScannerService] is real, since serializing is what the initializer
 * asks of it and the json of the row is the thing that has to survive.
 */
class ScannerInitializerTests {

    private val serializedScannerRepository: SerializedScannerRepository = mock()

    private lateinit var scannerService: ScannerService

    /** The scanners stored by the last run, one row per name rather than one per save. */
    private lateinit var stored: List<Scanner>

    /** What the fetcher answers for a page a worker fetches, so there is something to hand on. */
    private var fetchedHtml: String = ""

    @BeforeEach
    fun setUp() {
        scannerService = ScannerService(
            serializedScannerRepository,
            mock<ScraperService>(),
            mock<LocationService>(),
            mock<BookmarkService>()
        )

        // a save assigns an id, since the initializer logs one and a row without it was never stored
        whenever(serializedScannerRepository.save(any<Scanner>())).thenAnswer { invocation ->
            (invocation.getArgument<Scanner>(0)).also { if (it.id == null) it.id = 99 }
        }

        // nothing stored yet, so every default is created rather than found
        whenever(serializedScannerRepository.findAllByNameContaining(any())).thenReturn(emptyList())

        newInitializer().run(null)
        stored = storedScanners()
    }

    /** A fresh initializer, so a test may run one more time without the one of [setUp] being reused. */
    private fun newInitializer() = ScannerInitializer(serializedScannerRepository, scannerService)

    /**
     * The scanners of the last run, one row per name rather than one per save, since
     * [ScannerService.save] writes each row twice.
     */
    private fun storedScanners(): List<Scanner> {
        val captor = argumentCaptor<Scanner>()
        verify(serializedScannerRepository, atLeastOnce()).save(captor.capture())

        return captor.allValues.distinctBy { it.name }
    }

    /** the stored scanner named [name] */
    private fun storedScanner(name: String): Scanner = stored.single { it.name == name }

    /** the stored scanner named [name], run over [html], answering the elements it found */
    private fun runOver(name: String, html: String): List<FoundElement> {
        val kontext = kontext()

        scannerService.deserialize(storedScanner(name).serialization!!)
            .doWork(html, "http://example.org/galerie.html", kontext)

        return kontext.foundElements
    }

    /**
     * A kontext with a location of its own under [tempDir].
     *
     * A new folder per kontext, since a scan writes real files and [ScanPath] hands out a variant of a
     * name rather than overwriting one that is already on disk. Two runs into the same folder would
     * therefore record the second as `a.1.jpg`, and a test comparing two scrapers would be comparing
     * the state of the disk as much as the scrapers.
     */
    private fun kontext(): ScanningKontext {
        val folder = File(tempDir, "lauf-${++kontexts}").apply { mkdirs() }
        return ScanningKontext(location(folder), Mset(), arrayListOf(), fetcher, "")
    }

    private var kontexts = 0

    // -------------------------------------------------------------------------------------
    // The category link collector
    // -------------------------------------------------------------------------------------

    /**
     * The case this scraper was built for: the words of the links of one block, not the text of the
     * block.
     *
     * A selector on `#cnt_cats` itself would answer one element holding all three words and the
     * heading, so it is the count that says the links were picked rather than the block.
     */
    @Test
    fun `collects the words of the links under the category block`() {
        val html = """
            <html><body>
            <div id="cnt_cats">			Gallery Categories:<br><br>
            			<a href="/pics/2/Hearts.php">Hearts</a>, 			<a href="/pics/20/matti.php">matti</a>,
            <a ef="/pics/25/aces.php">Aces</a>		</div>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "Hearts"),
                FoundElement(1, "matti"),
                FoundElement(1, "Aces")
            ),
            runOver("Category Link Collector", html)
        )
    }

    /** A page with one category needs nothing changed, which is the point of a selector over the links. */
    @Test
    fun `collects a single word`() {
        val html = """<html><body><div id="cnt_cats"><a href="/a.php">Hearts</a></div></body></html>"""

        assertEquals(listOf(FoundElement(1, "Hearts")), runOver("Category Link Collector", html))
    }

    /** A page without that block answers nothing rather than failing, since nothing to collect is a result. */
    @Test
    fun `collects nothing from a page without that block`() {
        assertEquals(
            emptyList(),
            runOver("Category Link Collector", "<html><body><p>keine</p></body></html>")
        )
    }

    /** The word is what is asked for, so the whitespace a page writes around it does not matter. */
    @Test
    fun `trims the whitespace a page writes around a word`() {
        val html = """<html><body><div id="cnt_cats"><a href="/a.php">
              Hearts
            </a></div></body></html>"""

        assertEquals(listOf(FoundElement(1, "Hearts")), runOver("Category Link Collector", html))
    }

    /**
     * The label is what this reads, so a link whose href is misspelled on the page still has a word.
     *
     * A selector of `a[href]` would drop that category, so this is what says the two are not the same
     * scraper.
     */
    @Test
    fun `collects a word whose link has no href`() {
        val html = """<html><body><div id="cnt_cats"><a ef="/a.php">Aces</a></div></body></html>"""

        assertEquals(listOf(FoundElement(1, "Aces")), runOver("Category Link Collector", html))
    }

    /** Links outside the block belong to something else on the page and are not categories of it. */
    @Test
    fun `collects nothing from links outside the block`() {
        val html = """
            <html><body>
            <a href="/nav.html">Impressum</a>
            <div id="cnt_cats"><a href="/a.php">Hearts</a></div>
            </body></html>
        """.trimIndent()

        assertEquals(listOf(FoundElement(1, "Hearts")), runOver("Category Link Collector", html))
    }

    /** The block is picked by its id, so a second block of the same page is left alone. */
    @Test
    fun `collects the words of the block it is named after`() {
        val html = """
            <html><body>
            <div id="cnt_nav"><a href="/a.php">Impressum</a><a href="/b.php">Kontakt</a></div>
            <div id="cnt_cats"><a href="/c.php">Hearts</a></div>
            </body></html>
        """.trimIndent()

        assertEquals(listOf(FoundElement(1, "Hearts")), runOver("Category Link Collector", html))
    }

    /** The selector is on the links rather than on the block, which is the one thing to get right here. */
    @Test
    fun `reads the links under the block rather than the block itself`() {
        val worker = scannerService.deserialize(storedScanner("Category Link Collector").serialization!!)
            .worker as StructuredWorker

        val parser = assertIs<DomParser>(worker.scrapers.single().parser)
        assertEquals("#cnt_cats a", parser.tag)
        assertEquals("", parser.attribute)
    }

    // -------------------------------------------------------------------------------------
    // The gallery block link collector
    // -------------------------------------------------------------------------------------

    /**
     * The case this scraper was built for: every href of every link of that class, as a url that can
     * be visited rather than as the path the page wrote.
     */
    @Test
    fun `collects the hrefs of the gallery block links`() {
        val html = """
            <html><body>
            <a class="blk_galleries expp" href="/gallery/6577307">Gallery</a>
            <a class="blk_galleries expp" href="/gallery/6577311">Another one</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/gallery/6577307"),
                FoundElement(1, "http://example.org/gallery/6577311")
            ),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** A page with one of them needs nothing changed, since the class is the selector. */
    @Test
    fun `collects a single href`() {
        val html = """<html><body><a class="blk_galleries expp" href="/gallery/6577307">G</a></body></html>"""

        assertEquals(
            listOf(FoundElement(1, "http://example.org/gallery/6577307")),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** A page without such a block answers nothing rather than failing, since nothing to collect is a result. */
    @Test
    fun `collects nothing from a page without that class`() {
        assertEquals(
            emptyList(),
            runOver("Gallery Block Link Collector", """<html><body><a href="/a.html">A</a></body></html>""")
        )
    }

    /**
     * The class is what marks a gallery link, and the navigation links around it have none of them,
     * so they must not come along.
     */
    @Test
    fun `collects nothing from links without that class`() {
        val html = """
            <html><body>
            <a href="/impressum.html">Impressum</a>
            <a class="nav_link" href="/kontakt.html">Kontakt</a>
            <a class="blk_galleries expp" href="/gallery/6577307">Gallery</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/gallery/6577307")),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /**
     * A css selector matches a class wherever it sits in the attribute, so a page that leaves the
     * `expp` off one of them does not lose that gallery.
     */
    @Test
    fun `collects a link of the class without the second one`() {
        val html = """
            <html><body>
            <a class="blk_galleries" href="/gallery/1">A</a>
            <a class="expp blk_galleries" href="/gallery/2">B</a>
            <a class="expp" href="/gallery/3">C</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/gallery/1"),
                FoundElement(1, "http://example.org/gallery/2")
            ),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** The href and not the label, which is what the caller can do something with. */
    @Test
    fun `reads the href rather than the text of the link`() {
        val html = """<html><body>
            <a class="blk_galleries expp" href="/gallery/6577307">Galerieübersicht</a>
            </body></html>""".trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/gallery/6577307")),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** An absolute href on the page stays absolute rather than being resolved against the page again. */
    @Test
    fun `keeps an href the page wrote as an absolute one`() {
        val html = """
            <html><body>
            <a class="blk_galleries expp" href="https://other.example/gallery/7">A</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "https://other.example/gallery/7")),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** A link of that class without an href has none to gather, so it is not a finding. */
    @Test
    fun `collects nothing from a link of that class without an href`() {
        val html = """<html><body>
            <a class="blk_galleries expp">Gallery</a>
            <a class="blk_galleries expp" href="/gallery/1">A</a>
            </body></html>""".trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/gallery/1")),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** Only the gallery links of a page, and every one of them rather than the first. */
    @Test
    fun `collects every gallery link of the page in the order the page lists them`() {
        val html = """
            <html><body>
            <a class="blk_galleries expp" href="/gallery/9">9</a>
            <a class="blk_galleries expp" href="/gallery/3">3</a>
            <a class="blk_galleries expp" href="/gallery/7">7</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/gallery/9"),
                FoundElement(1, "http://example.org/gallery/3"),
                FoundElement(1, "http://example.org/gallery/7")
            ),
            runOver("Gallery Block Link Collector", html)
        )
    }

    /** The selector is on the class of the links, which is what tells them apart from the rest. */
    @Test
    fun `reads the links of that class rather than every link of the page`() {
        val worker = scannerService.deserialize(storedScanner("Gallery Block Link Collector").serialization!!)
            .worker as StructuredWorker

        val parser = assertIs<DomParser>(worker.scrapers.single().parser)
        assertEquals("a.blk_galleries", parser.tag)
        assertEquals("abs:href", parser.attribute)
    }

    /** A collector, so nothing is downloaded and no media is recorded. */
    @Test
    fun `collects the gallery links and nothing else`() {
        val kontext = kontext()
        val html = """
            <html><body>
            <a class="blk_galleries expp" href="/gallery/6577307">Gallery</a>
            <img src="/bilder/a.jpg">
            </body></html>
        """.trimIndent()

        scannerService.deserialize(storedScanner("Gallery Block Link Collector").serialization!!)
            .doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(
            listOf(FoundElement(1, "http://example.org/gallery/6577307")),
            kontext.foundElements
        )
        assertEquals(emptyList(), kontext.mset?.media?.toList(), "no media recorded")
    }

    // -------------------------------------------------------------------------------------
    // The last link collector
    // -------------------------------------------------------------------------------------

    /**
     * The case this scraper was built for: a page that writes its links one after the other and ends
     * in the link to the next one, where the caller wants that link and not the galleries above it.
     */
    @Test
    fun `collects the last link of a page that writes its links flat`() {
        val html = """
            <html><body>
            <a href="/gallery/1">First</a>
            <a href="/gallery/2">Second</a>
            <a href="/index.php?page=2">Next page</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/index.php?page=2")),
            runOver("Last Link Collector", html)
        )
    }

    /** One link is the last one as much as three are, so a page needs nothing changed. */
    @Test
    fun `collects the only link of a page`() {
        val html = """<html><body><a href="/only.html">Only</a></body></html>"""

        assertEquals(
            listOf(FoundElement(1, "http://example.org/only.html")),
            runOver("Last Link Collector", html)
        )
    }

    @Test
    fun `collects nothing from a page without links`() {
        assertEquals(
            emptyList(),
            runOver("Last Link Collector", """<html><body><p>keine links</p></body></html>""")
        )
    }

    /** The query of a pager link is part of it, or the second page would be the first one. */
    @Test
    fun `keeps the query of the last link`() {
        val html = """
            <html><body>
            <a href="/gallery/1">First</a>
            <a href="/index.php?page=2&sort=name">Next page</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/index.php?page=2&sort=name")),
            runOver("Last Link Collector", html)
        )
    }

    /**
     * The last one of the group, and one finding per group, since `:last-child` is about the children
     * of a parent and not about the last link of a document. Pinned so that the boundary of this
     * selector is a fact about the test rather than a surprise at run time.
     */
    @Test
    fun `collects the last link of each group of a page that groups them`() {
        val html = """
            <html><body>
            <div class="gallery"><a href="/gallery/1">First</a><a href="/gallery/2">Second</a></div>
            <div class="footer"><a href="/impressum">Impressum</a></div>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/gallery/2"),
                FoundElement(1, "http://example.org/impressum")
            ),
            runOver("Last Link Collector", html)
        )
    }

    /** The link collector and this one read the same elements, so this has to be a subset of its answer. */
    @Test
    fun `answers the last of what the link collector answers`() {
        val html = """
            <html><body>
            <a href="/one.html">One</a>
            <a href="/two.html">Two</a>
            <a href="/three.html">Three</a>
            </body></html>
        """.trimIndent()

        val all = runOver("Link Collector", html)
        val last = runOver("Last Link Collector", html)

        assertEquals(1, last.size, "one finding rather than one per link")
        assertEquals(all.last(), last.single(), "the last of them")
    }

    /** The href rather than the label, since it is a collector of links. */
    @Test
    fun `collects the href of the last link rather than its text`() {
        val html = """<html><body>
            <a href="/one.html">One</a>
            <a href="/last.html">Zurück zur Übersicht</a>
            </body></html>""".trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/last.html")),
            runOver("Last Link Collector", html)
        )
    }

    /**
     * The selector is what says "the last one", so it is the part of the stored scanner a caller edits:
     * pinned here so that changing it to a plain `a[href]` would fail a test rather than quietly make
     * this the link collector again.
     */
    @Test
    fun `reads the last link of each parent with the selector that says so`() {
        val worker = scannerService.deserialize(storedScanner("Last Link Collector").serialization!!)
            .worker as StructuredWorker

        val parser = assertIs<DomParser>(worker.scrapers.single().parser)
        assertEquals("a[href]:last-child", parser.tag)
        assertEquals("abs:href", parser.attribute)
        assertIs<FoundElementsWorker>(worker.scrapers.single().worker)
    }

    // -------------------------------------------------------------------------------------
    // The last history link collector
    // -------------------------------------------------------------------------------------

    /**
     * The case this scraper was built for: the last of a page's history anchors, whose href is a
     * fragment of the page itself, answered as the whole address so a caller can follow it.
     */
    @Test
    fun `collects the last history link of a page with its url prepended`() {
        val html = """
            <html><body>
            <a class="expp" rel="history" href="#13" title=""><img src="/mini/13.jpg" alt=""></a>
            <a class="expp" rel="history" href="#14" title=""><img src="/mini/14.jpg" alt=""></a>
            <a class="expp" rel="history" href="#15" title=""><img src="/mini/15.jpg" alt=""></a>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/galerie.html#15")),
            runOver("Last History Link Collector", html)
        )
    }

    /**
     * The reason the selector is this narrow: `:last-child` is about a parent, so a plain `a[href]` one
     * would answer the last link of the navigation and of the footer as well.
     */
    @Test
    fun `collects the last history link and none of the links around it`() {
        val html = """
            <html><body>
            <div class="head"><a href="/start.html">Start</a></div>
            <div class="history">
              <a class="expp" rel="history" href="#14"><img src="/mini/14.jpg" alt=""></a>
              <a class="expp" rel="history" href="#15"><img src="/mini/15.jpg" alt=""></a>
            </div>
            <div class="foot"><a href="/kontakt.html">Kontakt</a></div>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(FoundElement(1, "http://example.org/galerie.html#15")),
            runOver("Last History Link Collector", html)
        )
    }

    @Test
    fun `collects nothing from a page without history links`() {
        val html = """<html><body><a href="/irgendwas.html">A</a></body></html>"""

        assertEquals(emptyList(), runOver("Last History Link Collector", html))
    }

    /**
     * A page whose history anchors sit one per block would answer one per block, which is what
     * `:last-child` says. Not a defect of this scanner but a property of the selector, so it is stated
     * here rather than left to be found out.
     */
    @Test
    fun `collects the last history link of each block of a page that has several`() {
        val html = """
            <html><body>
            <div><a class="expp" rel="history" href="#1">one</a></div>
            <div><a class="expp" rel="history" href="#2">two</a></div>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/galerie.html#1"),
                FoundElement(1, "http://example.org/galerie.html#2")
            ),
            runOver("Last History Link Collector", html)
        )
    }

    /** Both attributes of the stored selector, since either one alone is a one word change. */
    @Test
    fun `reads the history anchors of that class and no other link`() {
        val worker = scannerService.deserialize(storedScanner("Last History Link Collector").serialization!!)
            .worker as StructuredWorker

        val parser = assertIs<DomParser>(worker.scrapers.single().parser)
        assertEquals("a.expp[rel=history]:last-child", parser.tag)
        assertEquals("abs:href", parser.attribute)
    }

    // -------------------------------------------------------------------------------------
    // The others
    // -------------------------------------------------------------------------------------

    /** The scraper of the images, since it is the one every other part of the application reads. */
    @Test
    fun `the image scraper takes the images of a page and names the set after the title`() {
        val html = """<html><head><title>Galerie</title></head><body><img src="/bilder/a.jpg"></body></html>"""

        val kontext = kontext()
        scannerService.deserialize(storedScanner("Image Scraper").serialization!!)
            .doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("a.jpg"), kontext.mset?.media?.map { it.name })
        assertEquals("Galerie", kontext.mset?.name)
    }

    /**
     * A page that shows an image twice is one medium, since a [Parser] says an answer it would say
     * twice once: without that the set held `a.jpg` and `a.1.jpg` for one file, because
 * [org.endy.pmczero.model.scraper.ScanPath] hands out a variant of a name rather than overwriting it.
 */
@Test
    fun `records an image the page shows twice once`() {
        val html = """<html><head><title>Galerie</title></head><body>
            <img src="/bilder/a.jpg"><img src="/bilder/a.jpg"><img src="/bilder/b.jpg">
            </body></html>"""

        val kontext = kontext()
        scannerService.deserialize(storedScanner("Image Scraper").serialization!!)
            .doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("a.jpg", "b.jpg"), kontext.mset?.media?.map { it.name })
    }

    /** A downloader has to be in it, since an import of it is refused without one. */
    @Test
    fun `the image scraper downloads what it records`() {
        val structured = scannerService.deserialize(storedScanner("Image Scraper").serialization!!)
            .worker as StructuredWorker

        val recovery = assertIs<RecoveryWorker>(structured.scrapers[2].worker)
        assertIs<FileDownloader>(recovery.worker)
    }

    /** The sequence spelling records and downloads in one scraper rather than in two branches. */
    @Test
    fun `the sequence image scraper records and downloads in one branch`() {
        val structured = scannerService.deserialize(
            storedScanner("Sequence Image Scraper").serialization!!
        ).worker as StructuredWorker

        // the title, then one scraper over the images
        assertEquals(2, structured.scrapers.size)
        assertIs<SetCreator>(structured.scrapers[0].worker)

        val sequence = assertIs<SequenceWorker>(structured.scrapers[1].worker)
        assertIs<MediaAdder>(sequence.workers[0])
        assertIs<FileDownloader>(assertIs<RecoveryWorker>(sequence.workers[1]).worker)
    }

    /** The two spellings of the same scraper have to agree, or one of them is a lie in a list. */
    @Test
    fun `the sequence image scraper takes the same images as the standard one`() {
        val html = """
            <html><head><title>Galerie</title></head><body>
            <img src="/bilder/a.jpg"><img src="/bilder/b.jpg">
            </body></html>
        """.trimIndent()

        val standard = kontext()
        scannerService.deserialize(storedScanner("Image Scraper").serialization!!)
            .doWork(html, "http://example.org/galerie.html", standard)

        val sequenced = kontext()
        scannerService.deserialize(storedScanner("Sequence Image Scraper").serialization!!)
            .doWork(html, "http://example.org/galerie.html", sequenced)

        assertEquals(standard.mset?.media?.map { it.name }, sequenced.mset?.media?.map { it.name })
        assertEquals(standard.mset?.name, sequenced.mset?.name)
    }

    /** The gallery index lister has to carry the nesting, since that is what makes it two levels. */
    @Test
    fun `the gallery index lister answers links and the images behind them`() {
        fetchedHtml = """<html><body><img src="/bilder/a.jpg"></body></html>"""

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/seite.html"),
                FoundElement(2, "http://example.org/bilder/a.jpg")
            ),
            runOver("Gallery Index Lister", """<html><body><a href="/seite.html">Seite</a></body></html>""")
        )
    }

    /** A collector, so it downloads nothing and records no media. */
    @Test
    fun `the link collector takes links and nothing else`() {
        val html = """<html><body><a href="/a.html">A</a><img src="/bilder/a.jpg"></body></html>"""

        assertEquals(
            listOf(FoundElement(1, "http://example.org/a.html")),
            runOver("Link Collector", html)
        )
    }

    // -------------------------------------------------------------------------------------
    // What a run stores
    // -------------------------------------------------------------------------------------

    @Test
    fun `stores every default under its own name`() {
        assertEquals(
            listOf(
                "Image Scraper",
                "Link Collector",
                "Image Lister",
                "Title Extractor",
                "Full Page Scraper",
                "Gallery Index Lister",
                "Sequence Image Scraper",
                "Category Link Collector",
                "Gallery Block Link Collector",
                "Last Link Collector",
                "Last History Link Collector"
            ),
            stored.map { it.name }
        )
    }

    /** A row that came back invalid would not be offered by findByUrl, so it would be unreachable. */
    @Test
    fun `stores every default as valid`() {
        assertTrue(stored.all { it.valid }, "every default is stored valid")
    }

    /** Nothing a default carries may be null but its name, or a stored scanner is missing part of itself. */
    @Test
    fun `stores a regex, an example and a scraper for every default`() {
        for (scanner in stored) {
            assertTrue(scanner.regex!!.isNotBlank(), "${scanner.name} has no regex")
            assertTrue(scanner.example!!.isNotBlank(), "${scanner.name} has no example")
            assertTrue(scanner.serialization!!.isNotBlank(), "${scanner.name} has no scraper")
        }
    }

    /** A second run must not create the same defaults again, which is what the name lookup is for. */
    @Test
    fun `creates nothing when every default is already stored`() {
        val existing = stored
        whenever(serializedScannerRepository.findAllByNameContaining(any()))
            .thenAnswer { invocation -> existing.filter { it.name == invocation.getArgument<String>(0) } }

        val writesBefore = writes()
        newInitializer().run(null)

        assertEquals(writesBefore, writes(), "a second run wrote a row")
    }

    /**
     * The number of rows written so far, which is what says a default was stored rather than found.
     *
     * Counting every save rather than the distinct scanners, since that is the thing that has to stop
     * growing: a second run that rewrote the same rows would leave [stored] looking unchanged.
     */
    private fun writes(): Int {
        val captor = argumentCaptor<Scanner>()
        verify(serializedScannerRepository, atLeastOnce()).save(captor.capture())
        return captor.allValues.size
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun location(folder: File) = Location().apply {
        id = 1
        name = "bilder"
        uri = folder.absolutePath
        locationType = LocationType.MAIN_FS.i
        inuse = 1
        this.storage = Storage()
    }

    @TempDir
    lateinit var tempDir: File

    /** A fetcher that answers [fetchedHtml] and writes what it downloads. */
    private val fetcher: Fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean) = fetchedHtml
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            target.parentFile?.mkdirs()
            return target.apply { writeText("content of $urlString") }
        }
    }
}
