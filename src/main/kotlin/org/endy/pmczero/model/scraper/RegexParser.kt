package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("regex")
class RegexParser(val regex: String) : Parser() {

    override fun findElements(text: List<String>, baseUri: String): List<String> {
        val result = regex.toRegex().findAll(text.first())
        return result.map {
            it.groupValues[1]
        }.filter { it != "" }.toList()
    }

}
