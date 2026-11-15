package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Answers [text] itself and then [n]-1 more of it, each with a number joined on.
 *
 * The one parser that does not read the page. Where every other parser answers what a page holds,
 * this answers what it is asked for: `text`, `text#1`, `text#2`, up to `text#<n-1>`, [n] answers out
 * of one in. That is what a site numbers itself and does not link — an index whose pages are the
 * same url with a number behind it, a gallery that serves its pages on request rather than writing
 * a link to each. [DomParser] and [RegexParser] can only answer what is written down; this answers
 * the urls that are not, which is why it takes a number and not a regex.
 *
 * The first answer is [text] as it is, and that is why [n] counts all of them and not the numbered
 * ones: `NumberedParser(1)` is the one answer a [PassThroughParser] gives, and `NumberedParser(5)`
 * is `text` plus four numbered behind it. An [n] of 0 or less answers nothing, which is what
 * [Parser.getElements] passes on and what a [Scraper] of it turns into an mset with no media, the
 * same loud failure an empty [SequenceParser] can be built into.
 *
 * [baseUri] is read and not used, the same as for [PassThroughParser]: there is nothing in the
 * answer that resolves against it, since the answer is the text it was handed with a number behind
 * it. What the text is — a full url, a relative one, a page number a later parser in a
 * [SequenceParser] turns into one — is not for this to say.
 *
 * The answers are [text] with `#<i>` joined on, and not with the number put anywhere in it, which is
 * a decision a caller shapes their [text] around: a url that wants its number after a `/` hands in
 * `http://example.org/page/` and reads `http://example.org/page/#1` out, so the separator belongs to
 * the text and not to this.
 *
 * @param n how many answers to give, the text itself counted in
 */
@Serializable
@SerialName("numbered")
class NumberedParser(val n: Int) : Parser() {

    override fun findElements(text: String, baseUri: String): List<String> =
        (0 until n).map { if (it == 0) text else "$text#$it" }
}
