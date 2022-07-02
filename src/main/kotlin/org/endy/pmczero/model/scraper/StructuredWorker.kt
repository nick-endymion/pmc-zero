package org.endy.pmczero.model.scraper

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.service.Downloader
import org.springframework.beans.factory.annotation.Autowired

@Serializable
@SerialName("StructuredWorker")
class StructuredWorker(
    val download: Boolean,
    val scanners: List<Scanner>
) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {

        var baseUri = ""
        val text = if (download) {
            baseUri = getBusUri(element)
            scanningKontext.downloader.getAsString(element)
        } else element
        for (scanner in scanners)
            scanner.doWork(text, baseUri, scanningKontext)
    }

    fun getBusUri(url: String): String {
        return "(.*/).*".toRegex().find(url)?.groupValues?.get(1) ?: throw Exception()
    }

}
