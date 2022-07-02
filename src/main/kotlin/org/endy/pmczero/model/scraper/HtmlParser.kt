package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

interface HtmlParser {

    fun getElements(text: String) : List<String>

}
