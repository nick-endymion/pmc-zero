package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A [DomParser] that answers the first element it finds and not the others.
 *
 * The same selector, the same attribute and the same regex as a [DomParser]; only the answer is
 * different. For a page whose first match is the one a caller is after — the main image a listing
 * leads with, the canonical link a header carries, the first gallery a page opens with.
 *
 * The first one **of the document**, and that is the whole point of it being a parser rather than a
 * selector, the same reading as [DomLastElememenParser] gives its last: `:first-child` is about a
 * parent, so `a[href]:first-child` answers the first link of each block a page writes, and no
 * narrowing of the selector can fix a page whose matches are each alone in a wrapper. Reading the
 * list jsoup hands over in document order and keeping its start answers one on any markup.
 *
 * ### Why this is a [Parser] and not a child of [DomParser]
 *
 * Not a stylistic choice, and the compiler has something to say about it. Two things:
 *
 * - `Parser` is `sealed`, and kotlinx.serialization resolves the subclasses of a sealed class into a
 *   `SealedClassSerializer` that knows each *direct* subclass of [Parser] and writes it under its own
 *   `@SerialName`. A child of [DomParser] is not one of those, so a scraper holding it could not be
 *   written as the type it is: it would go into `a.scanner.serialization` as a plain `dom` and come
 *   back out as a plain [DomParser], silently answering every element again.
 * - A `@Serializable` subclass of a `@Serializable` class carries its own primary constructor
 *   parameters as its own properties, and a subclass of [DomParser] takes only the three its parent
 *   already has. The plugin refuses such a class outright.
 *
 * So this holds the same three fields itself and asks a [DomParser] of its own for the elements, which
 * is the one line of [DomParser] that had to move out of its `findElements` for it. What a stored
 * scraper carries is the same as for a [DomParser] plus the `domFirstElement` type, which
 * [org.endy.pmczero.service.ScannerService] round trips.
 *
 * Nothing found is no answer at all: an empty list rather than a thrown exception, the same as
 * [DomLastElememenParser] answers nothing on a page without a match, since a page that does not carry
 * the element is a page the scraper finds nothing on and not one the scan fails on.
 *
 * @param regex what an answer has to match to be one
 * @param tag what the elements are picked with, as a jsoup selector
 * @param attribute what of each element is answered, its text where it is empty
 */
@Serializable
@SerialName("domFirstElement")
class DomFirstElememenParser(
    val regex: String,
    val tag: String,
    val attribute: String
) : Parser() {

    /**
     * The first of what a [DomParser] would have answered, in the order the page has them.
     *
     * The [DomParser] is built here rather than kept in a field, and that is what keeps it out of the
     * stored json: kotlinx.serialization writes every property that has a backing field, a field declared in
     * the body of a `@Serializable` class among them. A `private val delegate = DomParser(regex, tag,
     * attribute)` is therefore serialized as a second, nested parser object — three fields of the same
     * values twice, which any reader of `a.scanner.serialization` would have to know to ignore.
     *
     * `@Transient` is not the way to say it either: it wants a default value, and the default of this field
     * would be a `DomParser` over three empty strings, so a scanner read back from the database would carry
     * a delegate that matches nothing and answer nothing at all. A value that is not a field cannot be
     * serialized, and one that costs nothing to build does not have to be one.
     *
     * Built per call, which is one small object next to parsing and selecting over a whole page.
     */
    override fun findElements(text: List<String>, baseUri: String): List<String> =
        DomParser(regex, tag, attribute).elementsIn(text.first(), baseUri).take(1)
}
