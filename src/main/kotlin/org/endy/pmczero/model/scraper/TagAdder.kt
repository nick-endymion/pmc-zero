package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

/**
 * Adds the element it is handed as a tag of the mset of the scan, and does nothing else.
 *
 * The worker for what a word on a page is: a gallery listing the categories it belongs to, a page
 * naming a supplier, a listing of the years it holds. Those are labels rather than files, so
 * [MediaAdder] and [FileDownloader] are the wrong tools for them: a set of medium whose files do not
 * exist is a url that answers 404, and a category is not a file at all. Here the element lands on
 * [org.endy.pmczero.model.modern.Mset.tags] and nothing is fetched.
 *
 * Composes with the others rather than replacing them, in a [SequenceWorker] or as a branch of a
 * [StructuredWorker] beside a [FoundElementsWorker]. The two together are what a page of labels wants:
 * this to put them on the set, that to hand them back to a caller, and the same parser feeds both.
 *
 * Recorded as the element arrives rather than anything derived from it, since the point is the word
 * the page wrote. The whitespace around it is not this worker's business either: a
 * [org.endy.pmczero.model.scraper.DomParser] reading a blank attribute answers [org.jsoup.nodes.Element.text],
 * which normalises it, so a category spread over three lines arrives as one word.
 *
 * Nothing is deduplicated and nothing is lowercased. A page that lists the same word twice has listed
 * it twice, and whether `Amateur` and `amateur` are one tag is a question about the site, which is
 * where [org.endy.pmczero.model.modern.Scanner.supplierIdentifcator] and the rest of a stored scraper
 * are configured. Deciding that here would rewrite what a page said.
 *
 * @throws IllegalStateException when the scan has no set to tag, which is a scraper built with no
 * [SetCreator] and no mset in the kontext. A tag needs a set the way a medium does
 */
@Serializable
@SerialName("tagAdder")
class TagAdder : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        check(scanningKontext.mset != null)

        scanningKontext.mset!!.tags.add(element)
    }
}
