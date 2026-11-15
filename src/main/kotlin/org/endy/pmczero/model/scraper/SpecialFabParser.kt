package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("specialFab")
class SpecialFabParser() : Parser() {

    override fun findElements(text: List<String>, baseUri: String): List<String> =
        (0 until text.size).map { if (it == 0) text.first() else "${text.first()}#$it" }
}
