package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

/**
 * How a downloaded page is turned into the strings a [Worker] then acts on.
 *
 * `sealed` rather than `abstract`, and that is not a stylistic choice: [Scraper] holds a `Parser`, so
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

    abstract fun getElements(text: String, baseUri: String) : List<String>

}
