package org.endy.pmczero.model.scraper

class DomParser(val regex: Regex, tag: String, attribute: String) : HtmlParser() {

    override fun getElements(text: String): List<String> { //TODO via Jsoup
        val result = regex.findAll(text)
        return result.map {
            it.groupValues[1]
        }.filter { it != "" }.toList()
    }

}
