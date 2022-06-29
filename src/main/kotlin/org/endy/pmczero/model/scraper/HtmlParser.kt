package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

@Serializable
sealed abstract class HtmlParser {

    abstract fun getElements(text: String) : List<String>

}
