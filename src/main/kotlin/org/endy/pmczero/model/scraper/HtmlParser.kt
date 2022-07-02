package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

@Serializable
sealed class HtmlParser {

    abstract fun getElements(text: String) : List<String>

}
