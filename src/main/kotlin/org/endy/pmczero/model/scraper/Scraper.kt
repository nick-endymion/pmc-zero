package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
class Scraper(
    var parser: Parser,
    val worker: Worker
) {

    fun doWork(text: String, baseUri: String, scanningKontext: ScanningKontext) {
        // also on the kontext, because a [Worker] is handed an element and nothing else and cannot be
        // told where that element came from. A worker that hands its text to scrapers of its own, see
        // [StructuredWorker], has no way to pass [baseUri] on except through here, and a scraper that
        // reads an `abs:` attribute resolves it against exactly this.
        val previousBaseUri = scanningKontext.baseUri
        scanningKontext.baseUri = baseUri

        try {
            for (element in parser.getElements(text, baseUri)) {
                worker.applya(element, scanningKontext)
            }
        } finally {
            scanningKontext.baseUri = previousBaseUri
        }
    }

}
