package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
class Scraper(
    var parser: Parser,
    val worker: Worker
) {

    fun doWork(text: String, baseUri: String, scanningKontext: ScanningKontext) {
        for (element in parser.getElements(text, baseUri)) {
            worker.applya(element, scanningKontext)
        }
    }

}
