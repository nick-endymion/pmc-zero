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
    val fetcher: Fetcher,
    /**
     * The folder below [location] the files of this scan belong in, `/` separated and without a
     * leading or trailing slash. Blank means the location root itself.
     *
     * Part of the kontext rather than of the worker, because the path belongs to the scan and not to
     * the element: the worker is the same for every file of a run, whereas a scan writes one gallery
     * into `2020/august` and the next into `2021/march`. A worker that took the path as its own
     * property would have to be rebuilt for every scan, and every stored scraper would carry a path
     * that only meant anything at the moment it was configured.
     *
     * It is a folder and not a full file name, so the files of one scan stay together the way the
     * subdirectories of a scanned directory do, and two elements with the same file name do not
     * overwrite each other. The name of a single file is derived from the element by the worker.
     *
     * Blank rather than null for "no subfolder", so a caller does not have to decide between a
     * location root and a named one at every use.
     */
    var locationPath: String = ""
)
