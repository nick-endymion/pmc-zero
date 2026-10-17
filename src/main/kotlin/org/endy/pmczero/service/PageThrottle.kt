package org.endy.pmczero.service

/**
 * Keeps the pages a scraper fetches for itself apart in time, so a run does not walk a gallery index
 * as fast as the connection carries it.
 *
 * The pages are the ones a [org.endy.pmczero.model.scraper.StructuredWorker] with `download = true`
 * reads itself, i.e. the pages behind the links a scraper followed: the second level of a gallery
 * index, whatever a stored scraper nests under a link. A run over such a scraper asks a host for a
 * whole index and then for every page behind it back to back, and a host asked that fast answers with
 * errors, closes the connection half way, or blocks the address for everything behind it, which on an
 * index means every page rather than one of them.
 *
 * It cannot be paced by the worker alone, for the reason [DownloadThrottle] gives: every
 * [org.endy.pmczero.model.scraper.StructuredWorker] is built by kotlinx.serialization when a stored
 * scraper is read back, so each run brings one of its own and nothing about it outlives the run. What
 * does outlive the run is the moment the last page of the process was asked for, and that is all that
 * is kept here.
 *
 * ### Its own interval, not the one of [DownloadThrottle]
 *
 * The files of a scrape and the pages behind a gallery index are different requests to the same host,
 * and a deployment paces them for different reasons: a host that serves two hundred images slowly is
 * usually still happy to be asked for its pages quickly, and a run that fetches nothing at all has no
 * files to pace. So this is a second throttle rather than another setting of the first, configured by
 * its own property, and either can be on while the other is off.
 *
 * What is *not* counted here: the page a scan is given. That one is rendered by the service that
 * starts the run, see [org.endy.pmczero.service.ScraperImageImportService.importWith], and is fetched
 * once per run rather than once per element, so it costs no more than any other single page and is
 * left as it always was.
 *
 * What a wait is, and how it is counted across runs, scans and threads, is [Throttle].
 *
 * @see org.endy.pmczero.configuration.PageThrottleConfig for the property this is configured from
 */
object PageThrottle {

    /** the one pace of the process over pages, with the mechanics of it in [Throttle] */
    private val throttle = Throttle("page")

    /**
     * How many seconds have to pass between the start of one page fetch and the start of the next.
     *
     * 0, or anything below it, switches the throttle off, which is the default: an application that
     * configures nothing reads its pages as fast as it always did. Set per application in
     * `pmc.page.throttle-seconds`, see [org.endy.pmczero.configuration.PageThrottleConfig].
     *
     * Smaller than the file throttle is the usual choice, since a page is a bigger request than a file
     * and a gallery index is a handful of pages rather than hundreds of them: four seconds apart is a
     * gallery of fifty pages in under four minutes, while the same interval over its files would be a
     * quarter of an hour.
     */
    var seconds: Long
        get() = throttle.seconds
        set(value) {
            throttle.seconds = value
        }

    /**
     * Waits until a page may be fetched, and records that one is being fetched.
     *
     * Called before the request rather than after it, so the gap is between two starts, and called by
     * [org.endy.pmczero.model.scraper.StructuredWorker] rather than inside a fetcher, so it counts the
     * pages a run fetches for itself and nothing else.
     */
    fun awaitTurn() = throttle.awaitTurn()

    /**
     * Forgets the page that was fetched last and switches the throttle off.
     *
     * For tests, see [Throttle.reset]: the state is process wide, so a test that left a throttle on
     * would hold back every test that runs after it.
     */
    fun reset() = throttle.reset()
}