package org.endy.pmczero.model.scraper

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("SimpleParser")
class SimpleParser(val regex: String) : HtmlParser() {

    override fun getElements(text: String): List<String> {
        val regex = regex.toRegex()
        val result = regex.findAll(text)
        return result.map { it.value }.toList()
    }

}
