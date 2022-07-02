package org.endy.pmczero.model.scraper

class EqualityParser() : HtmlParser() {

    override fun getElements(text: String, baseUri: String): List<String> {
        return listOf(text)
    }

}
