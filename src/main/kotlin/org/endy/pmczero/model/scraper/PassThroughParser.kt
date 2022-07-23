package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("passThrough")
class PassThroughParser() : Parser() {
    override fun getElements(text: String, baseUri: String): List<String> {
        return listOf(text)
    }
}
