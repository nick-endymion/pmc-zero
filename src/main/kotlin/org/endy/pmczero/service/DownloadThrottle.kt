package org.endy.pmczero.service

/**
 * Keeps the downloaded files of this application apart in time, so a scrape does not fetch a gallery
 * as fast as the connection carries it.
 *
 * A gallery of two hundred images is two hundred requests back to back, and a host that is asked that
 * fast answers with errors, closes the connection half way, or blocks the address for everything
 * behind it. Nothing in the scraper pipeline paces that, and it cannot be paced by a worker alone:
 * every [org.endy.pmczero.model.scraper.FileDownloader] is built by kotlinx.serialization when a
 * stored scraper is read back, so each run brings one of its own and nothing about it outlives the
 * run. What does outlive the run is the moment the last download of the process began, and that is
 * all that is kept here.
 *
 * The pages a scraper fetches for itself are paced by [PageThrottle] instead, on its own interval:
 * they are requests to the same host but not the same requests, and a deployment that wants its files
 * kept five seconds apart does not thereby want its pages kept five seconds apart.
 *
 * What a wait is, and how it is counted across runs, scans and threads, is [Throttle]; what it costs is
 * here, and what it is for above.
 *
 * @see org.endy.pmczero.configuration.DownloadThrottleConfig for the property this is configured from
 */
object DownloadThrottle {

    /** the one pace of the process, with the mechanics of it in [Throttle] */
    private val throttle = Throttle("download")

    /**
     * How many seconds have to pass between the start of one download and the start of the next.
     *
     * 0, or anything below it, switches the throttle off, which is the default: an application that
     * does not configure a throttle downloads as fast as it always did. Set per application in
     * `pmc.download.throttle-seconds`, see [org.endy.pmczero.configuration.DownloadThrottleConfig].
     */
    var seconds: Long
        get() = throttle.seconds
        set(value) {
            throttle.seconds = value
        }

    /**
     * Waits until a download may start, and records that one is starting.
     *
     * Called before the request rather than after it, so the gap is between two starts, and called by
     * [org.endy.pmczero.model.scraper.FileDownloader] rather than inside a fetcher, so it counts the
     * files a scrape fetches and nothing else.
     */
    fun awaitTurn() = throttle.awaitTurn()

    /**
     * Forgets the download that ran last and switches the throttle off.
     *
     * For tests, see [Throttle.reset]: the state is process wide, so a test that left a throttle on
     * would hold back every test that runs after it.
     */
    fun reset() = throttle.reset()
}