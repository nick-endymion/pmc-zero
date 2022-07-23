package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable

@Serializable
abstract class Parser {

    abstract fun getElements(text: String, baseUri: String) : List<String>

}
