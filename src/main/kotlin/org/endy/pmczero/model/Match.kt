package org.endy.pmczero.model

import org.endy.pmczero.model.scraper.Scraper

class Match(val urlRegex: Regex) {

    val workers: List<Scraper> = listOf()

    fun match(url: String): Boolean {
        return urlRegex.matches(url)
    }


}
