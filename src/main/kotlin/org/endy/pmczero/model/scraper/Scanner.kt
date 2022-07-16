package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
//@SerialName("Scanner")
class Scanner(
    var htmlParser: HtmlParser,
    val worker: Worker
) {

    fun doWork(text: String, baseUri: String, scanningKontext: ScanningKontext) {
        for (element in htmlParser.getElements(text, baseUri)) {
            worker.applya(element, scanningKontext)
        }
    }

}
