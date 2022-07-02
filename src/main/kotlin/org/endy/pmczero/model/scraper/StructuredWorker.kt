package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.service.Downloader
import org.springframework.beans.factory.annotation.Autowired

class StructuredWorker(val download: Boolean, val scanners: List<Scanner>) : Worker() {

    lateinit var downloader: Downloader

    @Autowired
    fun getDownloader(downloader: Downloader){
        this.downloader = downloader
    }

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val downloadedHtml = ""; // from element todo

        val text = if (download) downloader.getAsString(element) else element

        for (scanner in scanners)
            scanner.doWork(text, scanningKontext)
    }

}
