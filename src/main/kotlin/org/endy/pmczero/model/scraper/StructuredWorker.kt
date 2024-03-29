package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
@SerialName("structured")
class StructuredWorker(
    val download: Boolean,
    val scrapers: List<Scraper>
) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {

        var baseUri = ""
        val text = if (download) {
            baseUri = getBusUri(element)
            println(baseUri)
            scanningKontext.downloader.getAsString(element)
        } else element
        for (scanner in scrapers)
            scanner.doWork(text, baseUri, scanningKontext)
    }

    fun getBusUri(url: String): String {
        return "(.*/).*".toRegex().find(url)?.groupValues?.get(1) ?: throw Exception()
    }

}
