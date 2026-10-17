package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.service.PageThrottle

/**
 * Hands its element to a list of scrapers in turn, fetching it first when asked to.
 *
 * ### How fast a page behind a link is read
 *
 * Not as fast as the connection carries it: a fetch waits out whatever is left of the configured
 * interval since the last one began, see [PageThrottle]. A scraper that follows links reads every page
 * behind them back to back, and a host asked for a whole gallery index at once answers with errors or
 * blocks the address, which on an index means every page rather than one of them.
 *
 * The wait is before the request and only in the branch that fetches, so two things hold that a wait
 * inside a fetcher could not: the page a scan was handed, which was rendered before the run started,
 * costs nothing here, and a scraper over html it is given rather than asked for costs nothing at all.
 * The interval is counted across runs rather than within one, so a scan started right after another one
 * ended still waits out the rest of it, and the throttle is off by default.
 *
 * @param download whether this worker fetches the element itself before handing it on. False for a
 * scraper over html the caller already has, which is the case for a page that had to be rendered in a
 * browser, since fetching it again would fetch the unrendered version of it
 * @param scrapers what the element is picked apart by, in the order they run
 */
@Serializable
@SerialName("structured")
class StructuredWorker(
    val download: Boolean,
    val scrapers: List<Scraper>
) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        // the base uri of the page the inner scrapers are handed, which is not the uri of [element] in
        // the branch that fetches: it is the uri of the html their text came out of. Reading an `abs:`
        // attribute against the wrong one leaves every relative url of a page unresolved, so a scraper
        // over html it was given would see no images where the same scraper over a fetched page sees
        // all of them. [Scraper.doWork] puts it on the kontext, which is how a worker that is only
        // handed an element passes it on.
        val baseUri = if (download) getBusUri(element) else scanningKontext.baseUri

        // handed on from the kontext, since a worker is given an element and nothing else and cannot ask
        // how long it may take to read it. Which is the whole point of the nested fetch: the page behind
        // a link is read for its markup, and waiting for its images to load costs the navigation timeout
        // of a gallery with one unreachable picture in it. See [ScanningKontext.waitForLoadEvent]
        val text = if (download) {
            // the throttle first, and only here: a host asked for every page of a gallery index back to
            // back answers with errors or blocks the address, so a page waits out whatever is left of the
            // configured interval since the last one began, across runs and scans alike. It is a pace of
            // its own rather than the one over the files, since a page and a picture are different
            // requests to the same host and a deployment may want one of them held back and not the
            // other, and it is before the request rather than after it, so a slow page does not push
            // every page behind it further out. See [PageThrottle]
            PageThrottle.awaitTurn()
            scanningKontext.fetcher.getAsString(
                element,
                waitForLoadEvent = scanningKontext.waitForLoadEvent
            )
        } else element

        for (scanner in scrapers)
            scanner.doWork(text, baseUri, scanningKontext)
    }

    fun getBusUri(url: String): String {
        return "(.*/).*".toRegex().find(url)?.groupValues?.get(1) ?: throw Exception()
    }

}
