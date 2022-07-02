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
//    @SerialName(
//        "download"
//    )
    val download: Boolean,
    val scanners: List<Scanner>
) : Worker() {

//    @Transient
//    lateinit var downloader: Downloader
//
//    @Autowired
//    fun getDownloader(downloader: Downloader){
//        this.downloader = downloader
//    }

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val downloadedHtml = ""; // from element todo

        val text = if (true) scanningKontext.downloader.getAsString(element) else element
        for (scanner in scanners)
            scanner.doWork(text, scanningKontext)
    }

}
