package org.endy.pmczero.ressource

import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState
import org.endy.pmczero.exception.BadRequestException

/**
 * The [WaitForSelectorState] a `waitForSelectorState` parameter names, null when it names none.
 *
 * Taken as a string rather than bound to the enum by spring, because spring's converter answers an
 * unknown name with null rather than with an error, and on a nullable parameter that null is
 * indistinguishable from a parameter that was not sent. A `waitForSelectorState=attaced` would then
 * be dropped and the call would go on to wait for the default, i.e. to fail with the very timeout
 * the parameter was meant to prevent, with nothing in the answer to say why.
 *
 * Blank is null rather than `visible` spelled out, so that a caller who says nothing keeps exactly
 * the behaviour of a caller who says nothing at all: the state is left unset on the playwright
 * options and the default of the library applies, which is what
 * [org.endy.pmczero.service.BrowserFetcher.waitFor] documents.
 *
 * Case is not significant, since a url query string is written by hand far more often than it is
 * generated, and the states are lower case in the documentation of playwright itself.
 *
 * @throws BadRequestException when [value] is not one of the four states
 */
fun waitStateOf(value: String?): WaitForSelectorState? =
    value?.takeIf { it.isNotBlank() }?.let { raw ->
        WaitForSelectorState.values().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: throw BadRequestException(
                "waitForSelectorState=$raw is not a wait state, expected one of " +
                    WaitForSelectorState.values().joinToString(", ") { it.name.lowercase() }
            )
    }

/**
 * The [WaitUntilState] a `waitUntil` parameter names, null when it names none.
 *
 * How far a page is waited for before its dom is read, which is a different question from
 * [waitStateOf]: that one is about an element on the page, this one is about the page having finished
 * arriving. See [org.endy.pmczero.service.BrowserFetcher.render] for what the states are worth.
 *
 * Null and not a state spelled out, so that a caller who says nothing keeps the behaviour that
 * playwright has by default, which is what every call did before there was a parameter to pick from.
 * A state that is quietly defaulted to another one would change which pages a call answers at all,
 * which is too much to guess at.
 *
 * `commit` is refused as a value even though playwright knows it: it fires when the response headers
 * have come back and the document has not been parsed at all, so the dom read after it is empty. It
 * is a state for a caller that wants to keep navigating and wait by hand, which is not a thing
 * [org.endy.pmczero.service.BrowserFetcher.render] can do.
 *
 * @throws BadRequestException when [value] is not one of the states worth waiting for
 */
fun waitUntilOf(value: String?): WaitUntilState? =
    value?.takeIf { it.isNotBlank() }?.let { raw ->
        val named = raw.trim()
        // commit excluded on purpose, see above
        val states = WaitUntilState.values().filterNot { it == WaitUntilState.COMMIT }

        states.firstOrNull { it.name.equals(named, ignoreCase = true) }
            ?: throw BadRequestException(
                "waitUntil=$named is not a state to wait for, expected one of " +
                    states.joinToString(", ") { it.name.lowercase() }
            )
    }
