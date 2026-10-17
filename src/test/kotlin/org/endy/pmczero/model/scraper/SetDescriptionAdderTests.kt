package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SetDescriptionAdder], which puts one piece of text on the set and does nothing else.
 *
 * The element is the only argument, so most of what is checked here is that it lands on the set as it
 * is and that the worker touches nothing else: an adder that also created a medium would make a scraper
 * of it write files and record media a caller never asked for.
 */
class SetDescriptionAdderTests {

    private lateinit var kontext: ScanningKontext

    @BeforeEach
    fun setUp() {
        kontext = ScanningKontext(location(), Mset(), arrayListOf(), fetcher, "")
    }

    // -------------------------------------------------------------------------------------
    // What it puts on the set
    // -------------------------------------------------------------------------------------

    @Test
    fun `puts the element on the set as its description`() {
        SetDescriptionAdder().applya("Eine Galerie von Herzen.", kontext)

        assertEquals("Eine Galerie von Herzen.", kontext.mset!!.description)
    }

    /** The sentence the page wrote, not a normalised version of it: what it says is what is recorded. */
    @Test
    fun `records the element exactly as it arrived`() {
        val text = "  Eine Galerie  von Herzen, gespielt von matti.\n"

        SetDescriptionAdder().applya(text, kontext)

        assertEquals(text, kontext.mset!!.description)
    }

    /** A paragraph rather than a word, since that is what the column is for. */
    @Test
    fun `puts a whole paragraph on the set`() {
        val long = "Ein Bild nach dem anderen. ".repeat(20)

        SetDescriptionAdder().applya(long, kontext)

        assertEquals(long, kontext.mset!!.description)
    }

    // -------------------------------------------------------------------------------------
    // What it leaves alone
    // -------------------------------------------------------------------------------------

    /**
     * The first of several, since a description is one piece of text and not a list: a parser that
     * matched more than one element has a caller that wants one of them.
     */
    @Test
    fun `puts only the first element it is handed`() {
        val worker = SetDescriptionAdder()

        worker.applya("Die erste Beschreibung.", kontext)
        worker.applya("Die zweite Beschreibung.", kontext)
        worker.applya("Die dritte Beschreibung.", kontext)

        assertEquals("Die erste Beschreibung.", kontext.mset!!.description)
    }

    /**
     * A description a person wrote through the api survives a second import of the gallery, which is the
     * rule [SetCreator] follows with a name and [MediaAdder] with a file name.
     */
    @Test
    fun `leaves a description that is already there`() {
        kontext.mset!!.description = "Von Hand geschrieben."

        SetDescriptionAdder().applya("Was die Seite sagt.", kontext)

        assertEquals("Von Hand geschrieben.", kontext.mset!!.description)
    }

    /**
     * Blank counts as missing rather than as something to keep: a set whose description is whitespace
     * is a set nobody described, so the sentence the page has is the better thing to leave behind.
     */
    @Test
    fun `describes a set whose description is blank`() {
        kontext.mset!!.description = "   "

        SetDescriptionAdder().applya("Was die Seite sagt.", kontext)

        assertEquals("Was die Seite sagt.", kontext.mset!!.description)
    }

    /** An empty description tag says nothing, and leaving the set alone is the honest answer. */
    @Test
    fun `puts nothing on a set for a blank element`() {
        SetDescriptionAdder().applya("   ", kontext)

        assertNull(kontext.mset!!.description)
    }

    // -------------------------------------------------------------------------------------
    // What it touches besides the description
    // -------------------------------------------------------------------------------------

    @Test
    fun `adds no media and no file`() {
        SetDescriptionAdder().applya("Eine Galerie von Herzen.", kontext)

        assertEquals(emptyList(), kontext.mset!!.media.toList())
        assertTrue(kontext.failures.isEmpty())
        assertTrue(kontext.foundElements.isEmpty(), "it describes the set rather than reporting it")
    }

    // -------------------------------------------------------------------------------------
    // What it refuses
    // -------------------------------------------------------------------------------------

    /** A description needs a set the way a tag does, so this is a scraper that was built wrong. */
    @Test
    fun `refuses a scan with no set`() {
        val without = ScanningKontext(location(), null, arrayListOf(), fetcher, "")

        assertThrows<IllegalStateException> {
            SetDescriptionAdder().applya("Eine Galerie von Herzen.", without)
        }
    }

    /** And it says what is missing, rather than failing on a null. */
    @Test
    fun `says what a scan with no set is missing`() {
        val without = ScanningKontext(location(), null, arrayListOf(), fetcher, "")

        val failure = assertThrows<IllegalStateException> {
            SetDescriptionAdder().applya("Eine Galerie von Herzen.", without)
        }

        assertTrue(
            failure.message!!.contains("SetCreator"),
            "the message names the worker that was left out: ${failure.message}"
        )
    }

    // -------------------------------------------------------------------------------------
    // How it is stored
    // -------------------------------------------------------------------------------------

    /** A stored scraper has to be able to carry this worker, which is what its serial name is for. */
    @Test
    fun `round trips under its serial name`() {
        val json = ScanFormat.json.encodeToString(
            Scraper(DomParser("(.+)", "meta[name=description]", "content"), SetDescriptionAdder())
        )

        assertTrue(json.contains("setDescriptionAdder"), "setDescriptionAdder expected in $json")

        val decoded = ScanFormat.json.decodeFromString<Scraper>(json)
        assertTrue(decoded.worker is SetDescriptionAdder, "it comes back as itself")
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun location() = Location().apply {
        id = 1
        name = "bilder"
        uri = File(System.getProperty("java.io.tmpdir"), "pmczero-description").absolutePath
        locationType = LocationType.MAIN_FS.i
        inuse = 1
        this.storage = Storage()
    }

    private val fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean, waitForLoadEvent: Boolean): String = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File = target
    }
}