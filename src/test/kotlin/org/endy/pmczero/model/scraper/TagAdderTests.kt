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
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [TagAdder], which records a word and does nothing else.
 *
 * The element is the only argument, so most of what is checked here is that it lands on the set as it
 * is and that the worker touches nothing else: a tag adder that also created a medium would make a
 * scraper of it write files and record media a caller never asked for, which is the failure the whole
 * worker exists to avoid.
 */
class TagAdderTests {

    private lateinit var kontext: ScanningKontext

    @BeforeEach
    fun setUp() {
        kontext = ScanningKontext(location(), Mset(), arrayListOf(), fetcher, "")
    }

    @Test
    fun `adds the element as a tag of the set`() {
        TagAdder().applya("Amateur", kontext)

        assertEquals(listOf("Amateur"), kontext.mset!!.tags)
    }

    /** The word the page wrote, not a normalised version of it: what it says is what is recorded. */
    @Test
    fun `records the element exactly as it arrived`() {
        TagAdder().applya("Amateur", kontext)

        assertEquals("Amateur", kontext.mset!!.tags.single())
    }

    @Test
    fun `adds every element it is handed`() {
        val worker = TagAdder()

        listOf("Amateur", "matti", "Aces").forEach { worker.applya(it, kontext) }

        assertEquals(listOf("Amateur", "matti", "Aces"), kontext.mset!!.tags)
    }

    /** A page that lists the same word twice has listed it twice, and the two rows are what was recorded. */
    @Test
    fun `does not deduplicate the tags`() {
        val worker = TagAdder()

        worker.applya("Amateur", kontext)
        worker.applya("Amateur", kontext)

        assertEquals(listOf("Amateur", "Amateur"), kontext.mset!!.tags)
    }

    /**
     * Whether `Amateur` and `amateur` are one tag is a question about the site, not about this worker,
     * so the case is left as the page wrote it.
     */
    @Test
    fun `keeps tags that differ only in case apart`() {
        val worker = TagAdder()

        worker.applya("Amateur", kontext)
        worker.applya("amateur", kontext)

        assertEquals(listOf("Amateur", "amateur"), kontext.mset!!.tags)
    }

    /** The order a page wrote them in is the order a caller showing them wants. */
    @Test
    fun `keeps the tags in the order they arrive`() {
        TagAdder().applya("zweiter", kontext)
        TagAdder().applya("erster", kontext)

        assertEquals(listOf("zweiter", "erster"), kontext.mset!!.tags)
    }

    /**
     * A worker that tagged and also stored would make a run write files, which is exactly what a caller
     * collecting the words of a page is avoiding.
     */
    @Test
    fun `writes nothing but the tags`() {
        TagAdder().applya("Amateur", kontext)

        assertEquals(0, kontext.mset!!.media.size)
        assertTrue(kontext.mset!!.media.all { it.bessources.isEmpty() }, "no bessources were created")
        assertEquals(emptyList(), kontext.foundElements)
        assertEquals(emptyList(), kontext.failures)
        assertEquals(emptyMap(), kontext.takenFileNames)
    }

    /** A tag needs a set the way a medium does, so there is nothing to tag without one. */
    @Test
    fun `refuses a scan without a set`() {
        kontext.mset = null

        assertThrows<IllegalStateException> {
            TagAdder().applya("Amateur", kontext)
        }
    }

    /**
     * A [SetCreator] replaces the set the kontext started with, so the tags have to go onto the new one.
     *
     * The order inside the [StructuredWorker] is what this is about: the set has to be created before
     * anything is tagged, or the tags land on a set that is thrown away with the first branch.
     *
     * A [PassThroughParser] at the top, since a [StructuredWorker] hands each of its scrapers the
     * element it was given: an outer parser that picked the links would hand the branches the words and
     * not the page.
     */
    @Test
    fun `tags the set a SetCreator replaced the kontext one with`() {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser("(.+)", "a", ""), TagAdder())
                )
            )
        )

        val html = """
            <html><head><title>Galerie</title></head><body>
            <a href="/pics/2/amateur.php">Amateur</a>
            <a href="/pics/20/matti.php">matti</a>
            </body></html>
        """.trimIndent()

        scraper.doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("Amateur", "matti"), kontext.mset!!.tags)
        assertEquals("Galerie", kontext.mset?.name)
    }

    // -------------------------------------------------------------------------------------
    // In a scraper
    // -------------------------------------------------------------------------------------

    /** The case the worker is for: the words of one block of a page, read as its links. */
    @Test
    fun `a scraper of it tags the set with the words of a block`() {
        val html = """
            <html><body>
            <div id="cnt_cats">			Gallery Categories:<br><br>
            			<a href="/pics/2/amateur.php">Amateur</a>, 			<a href="/pics/20/matti.php">matti</a>,
            <a ef="/pics/25/aces.php">Aces</a>		</div>
            </body></html>
        """.trimIndent()

        Scraper(
            DomParser("(.+)", "#cnt_cats a", ""),
            TagAdder()
        ).doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("Amateur", "matti", "Aces"), kontext.mset!!.tags)
    }

    /**
     * Both halves of what a page of labels wants, off one parser: the words on the set and the words
     * back to the caller.
     */
    @Test
    fun `tags the set and collects the words in one scraper`() {
        val html = """<html><body><div id="cnt_cats"><a href="/a.php">Amateur</a></div></body></html>"""

        Scraper(
            DomParser("(.+)", "#cnt_cats a", ""),
            SequenceWorker(listOf(TagAdder(), FoundElementsWorker(1)))
        ).doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("Amateur"), kontext.mset!!.tags)
        assertEquals(listOf(FoundElement(1, "Amateur")), kontext.foundElements)
    }

    /** A page with no such block answers a set without tags rather than failing. */
    @Test
    fun `tags nothing on a page without that block`() {
        Scraper(DomParser("(.+)", "#cnt_cats a", ""), TagAdder())
            .doWork("<html><body><p>keine</p></body></html>", "http://example.org/galerie.html", kontext)

        assertEquals(emptyList(), kontext.mset!!.tags)
    }

    /** Stored as configuration like any other worker. */
    @Test
    fun `round trips under its serial name`() {
        val json = ScanFormat.json.encodeToString(Scraper(DomParser("(.+)", "a", ""), TagAdder()))

        // on the whitespace of ScanFormat, which is pretty printed: this is a stored scraper, so the
        // json has to read as well as it writes
        assertTrue(json.contains(""""type": "tagAdder""""), "tagAdder expected in $json")

        val decoded = ScanFormat.json.decodeFromString<Scraper>(json)

        assertIs<TagAdder>(decoded.worker)
        assertEquals("a", (decoded.parser as DomParser).tag)
    }

    /** It nests like any other worker, so a sequence of steps may end in tagging. */
    @Test
    fun `runs as a step of a sequence`() {
        SequenceWorker(listOf(TagAdder(), TagAdder()))
            .applya("Amateur", kontext)

        assertEquals(listOf("Amateur", "Amateur"), kontext.mset!!.tags)
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

    /** Nothing is fetched, so this one writes nothing and would fail if anything tried. */
    private val fetcher: Fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean) =
            throw UnsupportedOperationException("a tag adder fetches nothing")

        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) =
            throw UnsupportedOperationException("a tag adder fetches nothing")

        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File =
            throw UnsupportedOperationException("a tag adder fetches nothing")
    }
}
