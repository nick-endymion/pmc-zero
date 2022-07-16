package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

@Serializable
abstract class HtmlParser {

    abstract fun getElements(text: String, baseUri: String) : List<String>

}
