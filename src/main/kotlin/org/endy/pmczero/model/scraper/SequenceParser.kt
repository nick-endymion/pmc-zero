package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hands what it finds to each of its [parsers] in turn, what one finds being what the next reads.
 *
 * The one thing the other parsers do not: they each answer the page as a whole, so an extraction
 * that needs two steps costs either a second scraper or a regex wide enough to do both jobs in one,
 * and a regex doing both jobs in one is the regex nobody can read again a month later. This runs
 * the parsers one after the other over the elements of the one before it:
 *
 *     Scraper(
 *         SequenceParser(listOf(
 *             DomParser("", "div.gallery", ""),
 *             DomParser("", "img[src]", "abs:src")
 *         )),
 *         MediaAdder()
 *     )
 *
 * answers every image of every gallery, where a single [DomParser] over `div.gallery img[src]`
 * answers the same only as long as the galleries do not nest, and answers every image of the page
 * the day one does. The first parser reads what a [Scraper] hands in, the page; every parser after
 * the first reads the elements of the one before it, so the list is a pipeline and not a list of
 * alternatives — [SequenceWorker] hands the same element to each of its workers, and this is the
 * parser of it, the element handed on rather than handed around.
 *
 * The parsers run in the order they are listed and the findings come out in the order that implies:
 * all of the first parser's elements, each with the second parser's findings inside it, and so on
 * down. That is document order at every step, which is what keeps the order [Scraper] hands to the
 * worker the order the page has things in, one nesting deep at a time.
 *
 * Each step runs through [Parser.getElements] and not through `findElements`, and that is not
 * shorthand: `findElements` is `protected`, and a [Parser] cannot call it on another parser at all,
 * so `getElements` is the only way in. It takes the duplicates out at every step, which is what the
 * chain wants — two galleries that hold the same image are two elements the second parser runs on
 * separately, and without the duplicates out at the step where they were found the same url comes
 * out of the chain once per gallery instead of once per page.
 *
 * An empty list answers the page itself, which is what [PassThroughParser] answers: a chain of no
 * steps is a pass-through, so the empty case is not a case of its own but the one parser that is
 * already there. The same holds one step up — [Parser.getElements] takes the duplicates out of the
 * chain's answer, the same as it does for any parser, so a chain and a single parser of one scraper
 * are deduped alike.
 *
 * @param parsers what the elements are handed to, in the order they are handed on
 */
@Serializable
@SerialName("sequence")
class SequenceParser(val parsers: List<Parser>) : Parser() {

    override fun findElements(text: String, baseUri: String): List<String> {
        var elements = listOf(text)
        for (parser in parsers) {
            elements = elements.flatMap { parser.getElements(it, baseUri) }
        }
        return elements
    }
}
