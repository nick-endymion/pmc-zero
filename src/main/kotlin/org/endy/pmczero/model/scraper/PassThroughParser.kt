package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("passThrough")
class PassThroughParser() : Parser() {
    override fun findElements(text: String, baseUri: String): List<String> {
        return listOf(text)
    }
}
