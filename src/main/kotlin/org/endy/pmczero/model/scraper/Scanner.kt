package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
class Scanner(
    var htmlParser: HtmlParser,
    val worker: Worker
) {

    fun doWork(text: String, scanningKontext: ScanningKontext) {
        for (element in htmlParser.getElements(text)) {
                worker.applya(element, scanningKontext)
        }
    }

}