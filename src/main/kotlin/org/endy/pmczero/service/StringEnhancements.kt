package org.endy.pmczero.service

fun String.getNameInUrl(): String {
    val f = this.split("/")
    val fn = f[f.size - 1].split("?")[0]
    return fn
}

fun String.getBaseUrl(): String =
    "(.*)/.*".toRegex().find(this)?.groupValues?.get(1) ?: throw Exception()
