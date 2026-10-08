package org.endy.pmczero.ressource

import com.microsoft.playwright.options.WaitForSelectorState
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
 * Shared by every ressource that takes the parameter, so that what a state name is worth is decided
 * in one place rather than once per endpoint.
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
