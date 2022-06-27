package org.endy.pmczero.model

abstract class Scanner {

    val worker : Worker? = null

    abstract fun getElements(text: String): List<String>

    fun apply() {

    }

}
