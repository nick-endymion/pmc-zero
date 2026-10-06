package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

/**
 * Hands its element to a list of scrapers in turn, fetching it first when asked to.
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

        val text = if (download) scanningKontext.fetcher.getAsString(element) else element

        for (scanner in scrapers)
            scanner.doWork(text, baseUri, scanningKontext)
    }

    fun getBusUri(url: String): String {
        return "(.*/).*".toRegex().find(url)?.groupValues?.get(1) ?: throw Exception()
    }

}
