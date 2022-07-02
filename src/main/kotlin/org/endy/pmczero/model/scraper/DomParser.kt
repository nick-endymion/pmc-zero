package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Domparser")
class DomParser(val regex: String, val tag: String, val attribute: String) : HtmlParser()  {

    override fun getElements(text: String): List<String> { //TODO via Jsoup
        val result = regex.toRegex().findAll(text)
        return result.map {
            it.groupValues[1]
        }.filter { it != "" }.toList()
    }

}
