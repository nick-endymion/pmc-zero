package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("domLastElement")
class DomLastElememenParser(
    val regex: String,
    val tag: String,
    val attribute: String
) : Parser() {

    override fun findElements(text: String, baseUri: String): List<String> =
        DomParser(regex, tag, attribute).elementsIn(text, baseUri).takeLast(1)
}
