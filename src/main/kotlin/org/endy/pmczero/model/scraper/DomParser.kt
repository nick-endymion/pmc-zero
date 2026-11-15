package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.select.Elements

@Serializable
@SerialName("dom")
class DomParser(val regex: String, val tag: String, val attribute: String) : Parser() {

    override fun findElements(text: List<String>, baseUri: String): List<String> = //TODO via Jsoup
        elementsIn(text.first(), baseUri)

    /**
     * What [tag] and [attribute] hold on [text], with the ones [regex] does not match dropped.
     *
     * The parsing itself, without the duplicates out, so that [DomLastElememenParser] can read the same
     * elements and keep only the last of them rather than write this a second time. `internal` rather
     * than private, which is what a second class in this package needs.
     */
    internal fun elementsIn(text: String, baseUri: String): List<String> {

        val doc = Jsoup.parse(text)
        doc.setBaseUri(baseUri)
        val elements: Elements = doc.select(tag)
        val rx = regex.toRegex()

        return elements.map {
            if (attribute.trim() == "") {
                it.text()
            } else
                it.attr(attribute)
        }.filter { rx.matches(it) }

//        for (element in elements) {
//            element.text()
//            val ux = element.attr("src")
//            println(ux)
//        }

    }

}
