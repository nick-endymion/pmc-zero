package org.endy.pmczero.model

class Match {

    val urlRegex: Regex = Regex("")

    val workers: List<Scanner> = listOf()

    fun match(url: String) : Boolean {
        return urlRegex.matches(url)
    }


}
