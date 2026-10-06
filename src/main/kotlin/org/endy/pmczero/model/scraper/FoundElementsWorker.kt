package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.FoundElement
import org.endy.pmczero.model.ScanningKontext

/**
 * Records every element it is handed in [ScanningKontext.foundElements], with its own [level], and
 * does nothing else.
 *
 * The worker of a scan that collects rather than stores. A [Worker] either writes something or records
 * media or fetches a page, so a scraper built out of those alone answers nothing: there is no way to
 * ask it what it saw. This one is that way, and it is what [org.endy.pmczero.ressource.ScraperRessource.draft]
 * and a caller of [org.endy.pmczero.service.ScraperService.scan] read when they want to know which
 * elements a page offers without anything being written.
 *
 * It records the element as it arrives rather than anything derived from it, since the point is to see
 * what the [Parser] handed over: a worker of level 3 sitting on an `a[href]` with `abs:href` records
 * the url the browser would follow, not the text of the link.
 *
 * The level is per element rather than read off the list, so two workers of different levels can run
 * over the same page in one scraper and the results stay tellable apart afterwards. What a level is
 * for is the caller's own bookkeeping: a scraper that takes the links of a page at level 1 and then
 * every image those pages hold at level 2 can mark them apart without two separate scrapers.
 *
 * Nothing is deduplicated. Two elements that happen to read the same are two findings, and a caller
 * that wants them once can say so with the parser, which is where the filtering belongs.
 */
@Serializable
@SerialName("foundElements")
class FoundElementsWorker(val level: Int) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        scanningKontext.foundElements.add(FoundElement(level, element))
    }
}
