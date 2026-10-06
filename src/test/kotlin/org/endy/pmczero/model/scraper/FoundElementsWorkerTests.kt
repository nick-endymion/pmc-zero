package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.endy.pmczero.model.FoundElement
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Fetcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FoundElementsWorker], which records and does nothing else.
 *
 * The level is the only argument and the only thing it changes, so most of what is checked here is that
 * it lands on each element and that it does not touch anything else: a worker that quietly wrote a file
 * or a medium would make a scraper of it do something a caller did not ask for.
 */
class FoundElementsWorkerTests {

    private lateinit var kontext: ScanningKontext

    @BeforeEach
    fun setUp() {
        kontext = ScanningKontext(location(), Mset(), arrayListOf(), fetcher, "")
    }

    @Test
    fun `records the element with its level`() {
        FoundElementsWorker(1).applya("http://example.org/a.jpg", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/a.jpg")), kontext.foundElements)
    }

    /** A level of zero is as good a level as any: the worker takes what it is given and does not judge. */
    @Test
    fun `records a level of zero`() {
        FoundElementsWorker(0).applya("http://example.org/a.jpg", kontext)

        assertEquals(listOf(FoundElement(0, "http://example.org/a.jpg")), kontext.foundElements)
    }

    @Test
    fun `records the elements in the order they arrive`() {
        val worker = FoundElementsWorker(2)

        listOf("first", "second", "third").forEach { worker.applya(it, kontext) }

        assertEquals(
            listOf(FoundElement(2, "first"), FoundElement(2, "second"), FoundElement(2, "third")),
            kontext.foundElements
        )
    }

    /**
     * The level travels with each element, so two workers of different levels can run over one page and
     * the results stay tellable apart afterwards.
     *
     * Two scrapers writing into one list is the case this exists for: the caller reads one list back
     * rather than one per worker.
     */
    @Test
    fun `keeps the elements of two levels apart in one list`() {
        FoundElementsWorker(1).applya("http://example.org/links/1", kontext)
        FoundElementsWorker(3).applya("http://example.org/bilder/a.jpg", kontext)
        FoundElementsWorker(1).applya("http://example.org/links/2", kontext)

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/links/1"),
                FoundElement(3, "http://example.org/bilder/a.jpg"),
                FoundElement(1, "http://example.org/links/2")
            ),
            kontext.foundElements
        )
    }

    /** A page that offers the same element twice found it twice, which is what a parser said. */
    @Test
    fun `does not deduplicate the elements`() {
        val worker = FoundElementsWorker(1)

        worker.applya("same", kontext)
        worker.applya("same", kontext)

        assertEquals(2, kontext.foundElements.size)
    }

    /** Recorded as it arrives, not normalised: the point is to see what the parser read. */
    @Test
    fun `records the element exactly as it arrived`() {
        FoundElementsWorker(1).applya("  padded  ", kontext)

        assertEquals("  padded  ", kontext.foundElements.single().element)
    }

    /**
     * A worker that collected and also stored would make a draft write files, which is exactly what a
     * caller asking for a draft is avoiding.
     */
    @Test
    fun `writes nothing but the list`() {
        val before = kontext.mset!!.media.size

        FoundElementsWorker(1).applya("http://example.org/a.jpg", kontext)

        assertEquals(before, kontext.mset!!.media.size, "no media were created")
        assertTrue(kontext.mset!!.media.all { it.bessources.isEmpty() }, "no bessources were created")
        assertEquals(emptyList(), kontext.failures, "nothing failed")
        assertEquals(emptyMap(), kontext.takenFileNames, "no file names were claimed")
    }

    /** A scan that only collects needs neither a location nor a folder to work. */
    @Test
    fun `records without a location that could receive files`() {
        val catchup = ScanningKontext(
            Location().also { it.name = "Catchup"; it.uri = ""; it.storage = Storage() },
            Mset(),
            arrayListOf(),
            fetcher,
            ""
        )

        FoundElementsWorker(1).applya("http://example.org/a.jpg", catchup)

        assertEquals(1, catchup.foundElements.size)
    }

    /** Several scans must not see each other's findings, which is what a fresh kontext per scan is for. */
    @Test
    fun `starts empty for a new scan`() {
        FoundElementsWorker(1).applya("http://example.org/a.jpg", kontext)

        val second = ScanningKontext(location(), Mset(), arrayListOf(), fetcher, "")

        assertEquals(emptyList(), second.foundElements)
    }

    // -------------------------------------------------------------------------------------
    // In a scraper
    // -------------------------------------------------------------------------------------

    /**
     * The point of the worker: a scraper of parsers and this answers with the elements it saw.
     *
     * A [Worker] has no other way to hand a result back, so without something like this the elements a
     * scan ran into are unreachable once the scan is over.
     */
    @Test
    fun `a scraper of it collects the elements of a page`() {
        val html = """
            <html><body>
            <a href="/links/eins.html">Eins</a>
            <a href="/links/zwei.html">Zwei</a>
            <img src="/bilder/drittes.jpg">
            </body></html>
        """.trimIndent()

        Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.+)", "a[href]", "abs:href"), FoundElementsWorker(1)),
                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), FoundElementsWorker(2))
                )
            )
        ).doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/links/eins.html"),
                FoundElement(1, "http://example.org/links/zwei.html"),
                FoundElement(2, "http://example.org/bilder/drittes.jpg")
            ),
            kontext.foundElements
        )
    }

    /** Relative urls are resolved before they arrive, so what is recorded is what a browser would follow. */
    @Test
    fun `records the resolved url of a relative element`() {
        Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(Scraper(DomParser("(.+)", "a[href]", "abs:href"), FoundElementsWorker(1)))
            )
        ).doWork("<html><body><a href='relativ.html'>x</a></body></html>", "http://example.org/unter/ordner/", kontext)

        assertEquals(
            listOf(FoundElement(1, "http://example.org/unter/ordner/relativ.html")),
            kontext.foundElements
        )
    }

    /** Stored as configuration like any other worker, level and all. */
    @Test
    fun `round trips under its serial name with its level`() {
        val json = ScanFormat.json.encodeToString(Scraper(PassThroughParser(), FoundElementsWorker(7)))

        // on the whitespace of ScanFormat, which is pretty printed: this is a stored scraper, so the
        // json has to read as well as it writes, and a caller may well be looking at it
        assertTrue(json.contains(""""type": "foundElements""""), "foundElements expected in $json")
        assertTrue(json.contains(""""level": 7"""), "the level expected in $json")

        val decoded = ScanFormat.json.decodeFromString<Scraper>(json)

        assertEquals(7, (decoded.worker as FoundElementsWorker).level)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun location() = Location().apply {
        id = 1
        name = "bilder"
        uri = "C:/tmp/bilder"
        locationType = LocationType.MAIN_FS.i
        inuse = 1
        this.storage = Storage()
    }

    private val fetcher: Fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean) = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File = target
    }
}
