package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.select.Elements

@Serializable
@SerialName("dom")
class DomParser(val regex: String, val tag: String, val attribute: String) : Parser() {

    override fun getElements(text: String, baseUri: String): List<String> { //TODO via Jsoup

        val doc = Jsoup.parse(text)
        doc.setBaseUri(baseUri)
        val elements: Elements = doc.select(tag)

        return elements.map {
            if (attribute.trim() == "") {
                it.text()
            } else
              it.attr(attribute)
        }

//        for (element in elements) {
//            element.text()
//            val ux = element.attr("src")
//            println(ux)
//        }

    }

}
