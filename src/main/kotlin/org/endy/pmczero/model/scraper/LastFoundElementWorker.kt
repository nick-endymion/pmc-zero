package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.FoundElement
import org.endy.pmczero.model.ScanningKontext

/**
 * Records only the last element it is handed, at its [level], and forgets the one before it.
 *
 * [FoundElementsWorker] with one finding instead of all of them, for a page whose last element is the
 * one a caller is after. That is a shape pages are written in: an index page whose links are its
 * galleries followed by the link to the next page, a listing whose pager sits at the foot of it. A
 * caller paging through such a page does not want two hundred gallery links to pick the one out of,
 * it wants the url of the page it is to read next, and this is what answers that.
 *
 * The replacement is by level rather than by equality, so this and a [FoundElementsWorker] of another
 * level can run over one page and the results stay tellable apart afterwards, which is the rule
 * [FoundElement.level] exists for. Two of these of the same level over one page would take turns
 * replacing each other and the answer would be whichever ran last, so a scraper that wants both of
 * two collections has to give them levels of their own.
 *
 * Nothing is deduplicated and nothing is compared: the last element is the last one that arrived,
 * whether or not it was seen before, since a parser that says an answer twice once has already thrown
 * the duplicates out.
 *
 * The element is recorded as the parser answered it, so a worker over a `a[href]` with `abs:href`
 * records the url the browser would follow rather than the text of the link.
 */
@Serializable
@SerialName("lastFoundElement")
class LastFoundElementWorker(val level: Int) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        // the finding of this level rather than this element: the level is what a caller tells two
        // collections apart by, and it is the one that says which of them this belongs to
        scanningKontext.foundElements.removeAll { it.level == level }
        scanningKontext.foundElements.add(FoundElement(level, element))
    }
}