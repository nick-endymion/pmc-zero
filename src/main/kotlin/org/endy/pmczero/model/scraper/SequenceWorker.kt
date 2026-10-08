package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

/**
 * Hands the element to each of its [workers] in turn, one after the other.
 *
 * The one thing [StructuredWorker] does not: it passes the element on rather than picking it apart
 * again. A [StructuredWorker] takes its element and hands each of its scrapers a set of sub elements
 * that a [Parser] found in it, so building "record this and then download it" out of one costs two
 * scrapers whose parsers have to agree on what "this" is. This takes the element as it is and gives
 * it to each worker as it is, which is what a list of steps over one element needs:
 *
 *     Scraper(
 *         DomParser("(.+)", "img[src]", "abs:src"),
 *         SequenceWorker(listOf(MediaAdder(), RecoveryWorker(FileDownloader())))
 *     )
 *
 * is the two scrapers of [org.endy.pmczero.service.ScraperImageImportService.scraperOf] without the
 * parsers written twice.
 *
 * The workers run in the order they are listed and each one is handed what the one before it left, so
 * the order is not cosmetic: [MediaAdder] before [FileDownloader] records the medium the download
 * then writes the file of, and a download that 404s fails before anything was recorded rather than
 * after. Put this inside a [RecoveryWorker] and the element survives a step that throws, since the
 * steps below it never run for that element; put a [RecoveryWorker] around a single step and only
 * that step is skipped. The two nest, and which one is meant is the difference between losing one
 * download and losing everything after it.
 *
 * Each worker is given the same [ScanningKontext], so what one records the next can read: a
 * [MediaAdder] before a [SetCreator] would build the medium on a set that does not exist yet, which
 * is why a stored scraper that runs a [SequenceWorker] puts the set creator first.
 *
 * An empty list does nothing, which is a scraper that finds the elements of a page and throws them
 * away. That is a configuration mistake rather than a case worth answering specially, and a [Scraper]
 * of it ends up as an mset with no media, which
 * [org.endy.pmczero.service.ScraperImageImportService.importWithStoredScanner] refuses as an import.
 *
 * @param workers what the element is handed to, in the order it is handed on
 */
@Serializable
@SerialName("sequence")
class SequenceWorker(val workers: List<Worker>) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        for (worker in workers) {
            worker.applya(element, scanningKontext)
        }
    }
}
