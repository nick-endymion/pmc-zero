package org.endy.pmczero.model

import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.Fetcher

data class ScanningKontext(
    var location: Location,
//    var storage: Storage,
    var mset: Mset?,
    var attributes: MutableList<String>,
    /**
     * Where the pages of a scrape come from, i.e. plain http or a rendered browser. See [Fetcher].
     *
     * Part of the kontext rather than of the scraper, so one scraper definition serves both and the
     * choice is made per scan.
     */
    val fetcher: Fetcher
)
