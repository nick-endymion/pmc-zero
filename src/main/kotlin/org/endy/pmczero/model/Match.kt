package org.endy.pmczero.model

import org.endy.pmczero.model.scraper.Scanner

class Match(val urlRegex: Regex) {

    val workers: List<Scanner> = listOf()

    fun match(url: String): Boolean {
        return urlRegex.matches(url)
    }


}
