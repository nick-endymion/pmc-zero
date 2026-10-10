package org.endy.pmczero.ressource

import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState
import org.endy.pmczero.exception.BadRequestException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the two wait parameters of the scrape endpoints.
 *
 * Both are read as strings rather than bound to an enum by spring, and both refuse a name they do not
 * know rather than dropping it: a state quietly ignored is a call that goes on to wait for the default
 * and then fails with the very timeout the parameter was meant to prevent, with nothing in the answer
 * to say why.
 */
class WaitParametersTests {

    // -------------------------------------------------------------------------------------
    // waitForSelectorState
    // -------------------------------------------------------------------------------------

    @Test
    fun `reads a selector state`() {
        assertEquals(WaitForSelectorState.ATTACHED, waitStateOf("attached"))
    }

    @Test
    fun `reads a selector state whatever its case`() {
        // a url query string is written by hand far more often than it is generated
        assertEquals(WaitForSelectorState.VISIBLE, waitStateOf("VISIBLE"))
        assertEquals(WaitForSelectorState.HIDDEN, waitStateOf("  Hidden  "))
    }

    /** Absent or blank is no state, so playwright's own default applies. */
    @Test
    fun `reads no selector state from nothing`() {
        assertNull(waitStateOf(null))
        assertNull(waitStateOf("   "))
    }

    @Test
    fun `refuses a selector state it does not know`() {
        val e = assertThrows<BadRequestException> {
            waitStateOf("attaced")
        }

        assertEquals(true, e.message!!.contains("attaced"))
        assertEquals(true, e.message!!.contains("attached"), "the message lists what it does know")
    }

    // -------------------------------------------------------------------------------------
    // waitUntil
    // -------------------------------------------------------------------------------------

    @Test
    fun `reads a wait until state`() {
        assertEquals(WaitUntilState.DOMCONTENTLOADED, waitUntilOf("domcontentloaded"))
        assertEquals(WaitUntilState.LOAD, waitUntilOf("load"))
        assertEquals(WaitUntilState.NETWORKIDLE, waitUntilOf("networkidle"))
    }

    @Test
    fun `reads a wait until state whatever its case`() {
        assertEquals(WaitUntilState.DOMCONTENTLOADED, waitUntilOf("DOMContentLoaded"))
    }

    /**
     * Absent, so the default of playwright applies, which is what every call did before there was
     * anything to pick.
     */
    @Test
    fun `reads no wait until state from nothing`() {
        assertNull(waitUntilOf(null))
        assertNull(waitUntilOf("   "))
    }

    @Test
    fun `refuses a wait until state it does not know`() {
        val e = assertThrows<BadRequestException> {
            waitUntilOf("domcontentloadedd")
        }

        assertEquals(true, e.message!!.contains("domcontentloadedd"))
        assertEquals(true, e.message!!.contains("domcontentloaded"), "the message lists what it does know")
    }

    /**
     * `commit` fires before the document has been parsed, so the dom read after it is empty.
     *
     * Refused rather than quietly accepted, since a caller asking for it would get an empty page and
     * no reason why. Waiting by hand after a navigation is not a thing these calls can do.
     */
    @Test
    fun `refuses commit, which would read the dom before it is parsed`() {
        val e = assertThrows<BadRequestException> {
            waitUntilOf("commit")
        }

        assertEquals(true, e.message!!.contains("commit"))
    }
}
