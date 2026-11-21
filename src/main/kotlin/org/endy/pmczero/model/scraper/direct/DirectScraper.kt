package org.endy.pmczero.model.scraper.direct

import org.endy.pmczero.model.ScanningKontext

/**
 * A scraper that handles one site directly instead of delegating to the generic scraper pipeline.
 *
 * A direct scraper owns the selectors and steps needed by its site, while the
 * [ScanningKontext] carries the state shared with the rest of the scan, such as the set, the target
 * location, and the fetcher.
 *
 * Implementations are Spring components and are discovered automatically, so no central registry or
 * additional registration type has to be maintained when another one is added.
 */
interface DirectScraper {

    /**
     * The name this implementation is selected by.
     *
     * The simple name of the implementation by default, so a class named `Fa` is selected as `Fa`
     * without any extra registration.
     */
    val name: String
        get() = javaClass.simpleName

    /**
     * Loads and processes the page at [url] as part of [scanningKontext].
     */
    fun load(url: String, scanningKontext: ScanningKontext)
}
