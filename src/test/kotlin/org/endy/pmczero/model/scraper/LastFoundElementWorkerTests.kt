package org.endy.pmczero.model.scraper

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
 * Unit tests for [LastFoundElementWorker], which records one element and nothing else.
 *
 * The whole of it is what happens to the one before, so most of what is checked here is that the
 * answer is one finding rather than a list of them, and that the replacement is the one of its own
 * level and of no other.
 */
class LastFoundElementWorkerTests {

    private lateinit var kontext: ScanningKontext

    @BeforeEach
    fun setUp() {
        kontext = ScanningKontext(location(), Mset(), arrayListOf(), fetcher, "")
    }

    @Test
    fun `records the element with its level`() {
        LastFoundElementWorker(1).applya("http://example.org/next.html", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/next.html")), kontext.foundElements)
    }

    /** A level of zero is as good a level as any: the worker takes what it is given and does not judge. */
    @Test
    fun `records a level of zero`() {
        LastFoundElementWorker(0).applya("http://example.org/next.html", kontext)

        assertEquals(listOf(FoundElement(0, "http://example.org/next.html")), kontext.foundElements)
    }

    /**
     * The point of the worker: three elements in, one finding out, and it is the one the page ends in.
     */
    @Test
    fun `keeps only the last element it is handed`() {
        val worker = LastFoundElementWorker(1)

        worker.applya("http://example.org/one.html", kontext)
        worker.applya("http://example.org/two.html", kontext)
        worker.applya("http://example.org/three.html", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/three.html")), kontext.foundElements)
    }

    /**
     * Replaced rather than ignored, so a scraper that is run twice over the same page answers once and
     * not once per run.
     */
    @Test
    fun `replaces the element it recorded before`() {
        val worker = LastFoundElementWorker(1)

        worker.applya("http://example.org/one.html", kontext)
        worker.applya("http://example.org/one.html", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/one.html")), kontext.foundElements)
    }

    /** The same element again is still the last one, since the parser throws duplicates out already. */
    @Test
    fun `keeps the element a page ends in the same one twice`() {
        val worker = LastFoundElementWorker(1)

        worker.applya("http://example.org/next.html", kontext)
        worker.applya("http://example.org/next.html", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/next.html")), kontext.foundElements)
    }

    /**
     * The level is what tells two collections of one page apart, so it is the finding of that level
     * that is replaced and no other.
     */
    @Test
    fun `keeps the elements of two levels apart in one list`() {
        LastFoundElementWorker(1).applya("http://example.org/links/1", kontext)
        LastFoundElementWorker(2).applya("http://example.org/images/1", kontext)
        LastFoundElementWorker(1).applya("http://example.org/links/2", kontext)

        assertEquals(
            listOf(
                FoundElement(2, "http://example.org/images/1"),
                FoundElement(1, "http://example.org/links/2")
            ),
            kontext.foundElements
        )
    }

    /**
     * A finding of [FoundElementsWorker] of another level is left alone, so a scraper can answer one
     * collection in full and the last of another.
     */
    @Test
    fun `leaves the findings of a worker that keeps them all`() {
        FoundElementsWorker(1).applya("http://example.org/links/1", kontext)
        FoundElementsWorker(1).applya("http://example.org/links/2", kontext)
        LastFoundElementWorker(2).applya("http://example.org/next.html", kontext)

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/links/1"),
                FoundElement(1, "http://example.org/links/2"),
                FoundElement(2, "http://example.org/next.html")
            ),
            kontext.foundElements
        )
    }

    /**
     * Two of these of one level over one page would take turns replacing each other, so the answer
     * would be whichever ran last. Stated here so that it is a decision and not a surprise.
     */
    @Test
    fun `leaves only the finding of whichever ran last`() {
        val worker = LastFoundElementWorker(1)

        worker.applya("http://example.org/first.html", kontext)
        FoundElementsWorker(1).applya("http://example.org/second.html", kontext)
        worker.applya("http://example.org/third.html", kontext)

        assertEquals(listOf(FoundElement(1, "http://example.org/third.html")), kontext.foundElements)
    }

    /** A page without elements hands this worker nothing, so it answers nothing rather than something empty. */
    @Test
    fun `records nothing when it is handed nothing`() {
        LastFoundElementWorker(1)

        assertEquals(emptyList(), kontext.foundElements)
    }

    /** It records and does nothing else, so a scraper of it writes no medium and no file. */
    @Test
    fun `records the element and writes nothing else`() {
        LastFoundElementWorker(1).applya("http://example.org/next.html", kontext)

        assertEquals(emptyList(), kontext.mset?.media?.toList())
        assertTrue(kontext.failures.isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a location of its own, since a worker must not be able to write into the folder of another test */
    private fun location() = Location().apply {
        id = 1
        name = "bilder"
        uri = File(System.getProperty("java.io.tmpdir"), "pmczero-last-found").absolutePath
        locationType = LocationType.MAIN_FS.idx
        inuse = 1
        this.storage = Storage()
    }

    private val fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean, waitForLoadEvent: Boolean): String = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File = target
    }
}