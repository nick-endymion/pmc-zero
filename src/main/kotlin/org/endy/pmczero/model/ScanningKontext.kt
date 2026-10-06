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
     * subdirectories of a scanned directory do. The name of a single file is derived from the element
     * by the worker, which is also what keeps two elements of the same name apart, see
     * [takenFileNames].
     *
     * Blank rather than null for "no subfolder", so a caller does not have to decide between a
     * location root and a named one at every use.
     */
    var locationPath: String = "",
    /**
     * The file names already handed out during this scan, so a second element of the same name gets a
     * variant of it rather than overwriting the first.
     *
     * Part of the kontext because the clash is between two elements of one run: a gallery that names
     * its images `1.jpg`, `2.jpg`, ... has no problem, but a page that links the same file name twice,
     * or links it and then links a thumb of the same name, would silently lose one of the two. The
     * counter has to run across the whole scan for that, and both the worker that records a medium and
     * the worker that downloads the file have to agree on the answer, which they cannot do unless the
     * state is somewhere they share.
     *
     * The name is keyed by the element it was derived from, so asking again for the same element
     * answers the same name rather than yet another variant. That is what lets
     * [org.endy.pmczero.model.scraper.MediaAdder] and
     * [org.endy.pmczero.model.scraper.FileDownloader] each ask without the second one of them
     * believing the first had claimed a name.
     */
    var takenFileNames: MutableMap<String, String> = mutableMapOf(),
    /**
     * The elements of this scan that could not be handled, with the reason each one did.
     *
     * Empty unless a scan asked to be told, see
     * [org.endy.pmczero.model.scraper.RecoveryWorker], which is what puts anything in here. A worker
     * that throws takes the whole scan down with it, and over the elements of a page that costs every
     * image that would have worked. So the failures a caller wants to hear about are collected here
     * rather than propagated, and a scan that records nothing in this list had no trouble.
     *
     * On the kontext because the workers have no other channel: [org.endy.pmczero.model.scraper.Worker]
     * answers nothing, so a failure can only be thrown or left behind, and a scan that is not allowed
     * to fail cannot throw.
     */
    var failures: MutableList<ScanFailure> = mutableListOf(),
    /**
     * The uri the [ScanningKontext.mset] of this scan is being built from, i.e. the page the elements
     * were found on. Blank when there is none, which is the case for a scan over a whole location.
     *
     * On the kontext rather than on a worker because a [org.endy.pmczero.model.scraper.Worker] is only
     * handed an element and has no way of knowing where that element came from, while resolving an
     * element needs exactly that. A [org.endy.pmczero.model.scraper.DomParser] reading an `abs:` attribute
     * answers the url the page served it under, so without this a page that writes `/bilder/1.jpg` and a
     * page that writes it out in full are told apart, and only one of them yields a usable url.
     *
     * Saved and restored by the [org.endy.pmczero.model.scraper.StructuredWorker] around its inner
     * scrapers, which each run over a page of their own: a nested one that fetched its page must not
     * leave that uri behind for the scraper after it.
     */
    var baseUri: String = "",
    /**
     * The elements this scan ran into, each with the [FoundElement.level] of the worker that found it.
     *
     * What a scan collects when it is not collecting anything else: a worker that only records does
     * not write files, create media or set a name, so a scraper built out of those alone answers
     * nothing at all. This is where the elements it saw end up instead, which is what makes such a
     * scraper worth running, see [org.endy.pmczero.model.scraper.FoundElementsWorker].
     *
     * On the kontext rather than on the worker, since the list is the result of the whole scan and a
     * worker is only handed one element at a time. It also keeps two workers of one scraper, which run
     * over the same elements, from each having to hand a result back to whoever called them.
     */
    var foundElements: MutableList<FoundElement> = mutableListOf()
)
