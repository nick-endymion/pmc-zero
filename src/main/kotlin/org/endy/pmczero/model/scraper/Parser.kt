package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

/**
 * How a downloaded page is turned into the strings a [Worker] then acts on.
 *
 * `sealed` rather than `abstract`, and that is not a stylistic choice: [Scraper] holds a [Parser], so
 * serializing a scraper means serializing polymorphically. kotlinx.serialization resolves that
 * differently for the two:
 *
 * - a `sealed` base gets a `SealedClassSerializer`, which knows every subclass in the package and
 *   writes each one under its `@SerialName` on its own,
 * - an `abstract` base gets a `PolymorphicSerializer`, which knows nothing and answers
 *   "Class 'X' is not registered for polymorphic serialization in the scope of 'Parser'" for
 *   anything that was not added to a `SerializersModule` by hand.
 *
 * Since a plain `Json` carries no such module, an `abstract` Parser only serializes for a caller who
 * happens to have registered all three parsers, i.e. for [org.endy.pmczero.service.ScannerService]
 * and for nobody else. [Worker] is sealed for the same reason, which is why a scraper that fails
 * complains about the parser and never about the worker.
 *
 * The cost is that a parser cannot be added from outside this package. Adding one here is enough:
 * a subclass of a sealed class needs no annotation beyond `@Serializable` and a `@SerialName`.
 */
@Serializable
sealed class Parser {

    /**
     * What this finds in [text], with the answers that say the same thing twice said once.
     *
     * A page repeats itself: the same image in the teaser and in the gallery, the same link in the
     * header and in the footer, the same url twice because a page lists a gallery in two blocks. A
     * worker acts on one element at a time and has nothing to say about having seen it already, so
     * two findings that are the same string produce a second medium for the same file, a second
     * download of it, and a second line in a result that says nothing was different about it. Which of
     * those a caller wants is not for each of them to decide, so it is decided once, here.
     *
     * The first of a run of equal answers is the one kept, since a parser answers in document order
     * and the order is what a worker hands to [ScanPath.freeFileNameOf] to name things by.
     *
     * Equal means equal as a string and nothing more. A parser answers strings and not urls, so
     * `/gallery/1` and `http://example.org/gallery/1` are two answers here even where they are one
     * page, and a caller that wants them one has to resolve them itself, since [DomParser] has already
     * had its say about what an attribute means.
     *
     * Not across parsers either: two parsers of one scraper are two questions about a page and are
     * answered independently, which is what lets [org.endy.pmczero.model.FoundElement] tell a level 1
     * finding apart from the level 2 finding of the same url.
     */
    fun getElements(text: String, baseUri: String): List<String> =
        findElements(text, baseUri).distinct()

    /**
     * What a parser of this kind finds, in the order the page has it and with its duplicates still in.
     *
     * The raw half of [getElements], which is what every implementation answers and what takes the
     * duplicates out once for all of them: a parser added later cannot forget to, since forgetting is
     * not something the compiler can be asked about.
     */
    protected abstract fun findElements(text: String, baseUri: String): List<String>
}