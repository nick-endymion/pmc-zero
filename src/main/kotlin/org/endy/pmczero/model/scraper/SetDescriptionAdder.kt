package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

/**
 * Puts the element it is handed on the mset of the scan as its description, and does nothing else.
 *
 * The worker for the sentence a page writes about itself: the `meta[name=description]` of a gallery
 * page, the introductory paragraph of an article, the one line a listing says under its title. That is
 * a description and not a tag, so [TagAdder] is the wrong tool for it — a tag is one word and this is
 * prose — and it is not a file either, so [MediaAdder] and [FileDownloader] are wrong too. Here the
 * element lands on [org.endy.pmczero.model.modern.Mset.description] and nothing is fetched.
 *
 * Composes with the others rather than replacing them, in a [SequenceWorker] or as a branch of a
 * [StructuredWorker] beside a [FoundElementsWorker]: the same parser can feed both, so a caller gets
 * the description on the set and the text back in the result of the run.
 *
 * ### Only the first element, and only where there is none
 *
 * Two rules, and both are about a set that already exists:
 *
 * - **The first element wins.** A description is one piece of text, not a list, so a parser that
 *   matches several elements has a caller that wants one of them rather than all. The first is the one,
 *   since it is the one a page puts where a description belongs, and a caller who wants a different one
 *   narrows the selector rather than having this worker read further into the list. The others are
 *   dropped silently, the way [LastFoundElementWorker] keeps the last instead: the answer of a worker
 *   is the set, not a report of what was on the page.
 * - **A set that already has a description keeps it.** A scan runs over a gallery again whenever
 *   somebody imports it twice, and a description written by a person through the api would otherwise
 *   be replaced by whatever the page says this time, which may be nothing at all. This is the same rule
 *   [SetCreator] follows with the name of a set and [MediaAdder] with a name of a file: a scan fills in
 *   what is missing and leaves what is there alone.
 *
 * Blank counts as missing, since a page whose description tag is empty describes nothing and a set
 * described by an empty string is a set nobody described.
 *
 * Recorded as the element arrives rather than anything derived from it, since the point is the sentence
 * the page wrote. The whitespace around it is not this worker's business: a [DomParser] reading a blank
 * attribute answers [org.jsoup.nodes.Element.text], which normalises it, so a description spread over
 * three lines of html arrives as one paragraph.
 *
 * @throws IllegalStateException when the scan has no set to describe, which is a scraper built with no
 * [SetCreator] and no mset in the kontext. A description needs a set the way a tag does
 */
@Serializable
@SerialName("setDescriptionAdder")
class SetDescriptionAdder : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val mset = scanningKontext.mset
        check(mset != null) { "the scan has no set to describe, see SetCreator" }

        if (!mset!!.description.isNullOrBlank()) return
        if (element.isBlank()) return

        mset.description = element
    }
}