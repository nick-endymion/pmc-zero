package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.service.BookmarkService
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScraperImageImportService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.to.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scanners")
class ScannerRessource(
    val scannerService: ScannerService,
    val locationService: LocationService,
    val msetService: MsetService,
    val bookmarkService: BookmarkService,
    val scraperImageImportService: ScraperImageImportService
) {

    @GetMapping("/{id}")
    fun getserializedScanner(@PathVariable id: Int): ScannerTO {
        return scannerService.findById(id).toTO()
    }

    @PostMapping("/") //todo
    fun createserializedScanner(@RequestBody scannerTO: ScannerTO): ScannerTO {
        if (scannerTO.id != null) throw Exception()
        return scannerService.save(scannerTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveserializedScanner(
        @PathVariable id: Int,
        @RequestBody scannerTO: ScannerTO
    ): ScannerTO {
        if (id != scannerTO.id) throw Exception()
        return scannerService.save(scannerTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteserializedScanner(@PathVariable id: Int) {
        return scannerService.delete(id)
    }

//    @PostMapping("/bla")
//    fun XcreateserializedScanner(@RequestBody serializedScanner: SerializedScannerTO) : SerializedScannerTO {
////        if (serializedScannerTO.id != null) throw Exception()
//        return serializedScannerService.DeserializeAndSerialize(serializedScanner.toEntity()).toTO()
//    }

    @GetMapping("/")
    fun getserializedScanner(@RequestParam searchTerm: String?): List<ScannerShortTO> {
        return scannerService.findByUrl(searchTerm).map { it.toTO() }
    }

    @GetMapping("/{id}/scan")
    fun scan(@PathVariable id: Int, @RequestParam url: String): ScanningResultTO {
        val mset = scannerService.scan(id, url)
        val urls = mset.media.flatMap { it.bessources.map { it.name ?: "" } }
        val (commonUrlStart, locations) = locationService.getLocationStartingWith(urls)
        return ScanningResultTO(mset.toTOwithMedia(true), commonUrlStart, locations.map { it.toTO() })
    }

    /**
     * The images of [url], picked by the scraper stored as the scanner with [id], fetched into the
     * location with [locationId], answered in the same [ImageImportTO].
     *
     * The import of [org.endy.pmczero.ressource.ScraperRessource.importImagesWithScraper], with the
     * scraper read from the database rather than built from a `pattern`. So this is the call for a
     * scraper that is worth keeping: one stored through [ScannerService.save] and run from here on
     * every gallery that fits it, rather than written out per call. The built scraper is the right one
     * for a one off, and is still there as `/api/scrape/scraper-images`.
     *
     * That is the only difference from that endpoint, and it is worth being precise about what it
     * buys. A stored scraper can be anything, not just the images of a page: a `data-` attribute, a
     * css selector, a regex of its own, any nesting of them, which is what
     * `/api/scrape/scraper-import` takes as json and what the store here already holds. `pattern` is
     * therefore gone from the parameters, since there is no single `img[src]` regex left to narrow.
     *
     * The stored scraper is held to what an import needs of it: it has to write the files of the
     * media it records, so one without a [org.endy.pmczero.model.scraper.FileDownloader] is refused
     * with a 409 rather than answered with a set whose every url points at a file that was never
     * written.
     *
     * Answers 404 when no scanner has that id or when the location does not exist, and 409 when the
     * location cannot receive files or the browser cannot be started.
     *
     * @param id the scanner to run, i.e. the row of `a.serialized.scanner` whose serialization holds
     * the scraper
     * @param url the page to run it over
     * @param locationId the MAIN_FS location the files are written into, since there has to be a
     * directory to write them into
     * @param name the name of the mset and of the folder the files go into. Blank names the set after
     * the page title the scraper picks up
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See
     * [org.endy.pmczero.service.BrowserFetcher.render]
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible
     * @param scrollTimes how often the page is scrolled before collecting, since a lazily loading
     * gallery appends its images while scrolling
     * @param persist false answers the draft without writing the media to the database. The files are
     * written either way
     */
    @PostMapping("/{id}/scrape")
    fun scrape(
        @PathVariable id: Int,
        @RequestParam url: String,
        @RequestParam locationId: Int,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int,
        @RequestParam(defaultValue = "true") persist: Boolean
    ): ImageImportTO = scraperImageImportService.importWithStoredScanner(
        scannerId = id,
        locationId = locationId,
        url = url,
        name = name,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = waitStateOf(waitForSelectorState),
        persist = persist
    )

    @PostMapping("/{id}/scan")
    fun scan(@RequestBody sts: SourceToScanTO): ScanningResultTO {
        if (sts.scannerId == null) throw Exception()
        var mset = scannerService.scan(sts)
        if (sts.persist == true) {
            mset = msetService.save(mset)
            if (sts.bookmarkId != null) {
                msetService.addMedium(mset, bookmarkService.findById(sts.bookmarkId!!).medium!!)  //todo npe possible
             }
        }
        return ScanningResultTO(mset.toTOwithMedia(true), "", listOf(locationService.findById(sts.locationId!!).toTO()))
    }

}
