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

    /**
     * The scanner with [id], stored again as a new one, answered as the new scanner.
     *
     * For a scraper that is worth tweaking rather than rebuilding: the copy is the one to change a
     * regex or a selector on, so the scanner it came from stays as it is and keeps answering for the
     * pages it was made for. Nothing of the original is touched.
     *
     * A POST rather than a GET, since it writes a row, which for the same reason as the rest of this
     * application means a crawler or a prefetch would not fire it off by following a link.
     *
     * Answers 404 when no scanner has that id.
     *
     * @param id the scanner to copy
     * @param name the name of the copy, "<name> (copy)" when blank
     */
    @PostMapping("/{id}/copy")
    fun copyScanner(@PathVariable id: Int, @RequestParam(required = false) name: String?): ScannerTO =
        scannerService.copy(id, name).toTO()

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
     * ### Fetching the files later
     *
     * `noDownload` runs the whole import and fetches none of the files, for a gallery whose files are
     * not to be had yet: behind a login, on a slow host, or because they are to be fetched by hand.
     * The page is rendered, the set is named, the media are recorded and the set is saved exactly as
     * it would be otherwise, so a caller sees what the page holds and the set is there to fetch into
     * later.
     *
     * What differs is the files: each element the [org.endy.pmczero.model.scraper.FileDownloader] was
     * asked for goes into [ImageImportTO.failures] with the reason `Download excluded. Need to be done
     * manually`, one entry per file, and [ImageImportTO.failed] counts them. Nothing is thrown and the
     * run does not fail, which is the point: a file that was not fetched is a known gap rather than a
     * broken call. It is reported where a download that 404s is reported, since a caller cannot act on
     * the two differently and would otherwise have to read the reason to tell them apart.
     *
     * The media are recorded either way, so their bessources name the files as they will be once they
     * are fetched. A set saved this way therefore points at files that are not on disk yet, which is
     * what makes it worth a second run of the same call without it later.
     *
     * ### A file that is already there
     *
     * By default a file the location already holds is left alone. The medium is still recorded, under
     * the name that file has, and the file is reported in [ImageImportTO.failures] with the reason it
     * was not fetched again. So importing a gallery twice answers a set whose media all point at files
     * that exist plus a list of what was not fetched, rather than a second copy of every picture under
     * a numbered name.
     *
     * `alwaysNewDownload` asks for those copies instead, for a caller that wants the same picture twice
     * under two names. The file that is there is kept and the new one written beside it as
     * `bild.1.jpg`, so nothing already stored is overwritten either way.
     *
     * Answers 404 when no scanner has that id or when the location does not exist, and 409 when the
     * location cannot receive files or the browser cannot be started.
     *
     * @param id the scanner to run, i.e. the row of `a.serialized.scanner` whose serialization holds
     * the scraper. Also recorded on the set this call builds, see
     * [org.endy.pmczero.model.modern.Mset.scannnerId], so a set can be traced back to the scanner that
     * produced it without keeping the json of that scanner anywhere. No other import records it, since
     * a caller that only posts a scraper is not running one of the stored ones
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
     * @param supplierId the id of the supplier this page belongs to, e.g. the `4711` of
     * `https://example.org/artikel/4711/fotos`. Recorded on the set this call builds, see
     * [org.endy.pmczero.model.modern.Mset.supplierId]. Blank leaves the set without one, and the value
     * is not worked out of the url here: what an id looks like is a property of the site, configured
     * per scanner as a
     * [org.endy.pmczero.model.modern.Scanner.supplierIdentifcator], and whatever the caller read out
     * of the url is what gets stored
     * @param noDownload true runs the whole import and fetches none of the files. See above
     * @param alwaysNewDownload true fetches a file again even when the location already holds it,
     * under a name of its own. False, the default, leaves that file alone and reports it. See above
     * @param waitUntil how far to wait for the page before its dom is read. Blank waits for the load
     * event, which counts the images of the page, so a gallery of two hundred holds the answer up
     * until all two hundred are in. `domcontentloaded` is the one for a caller that wants the markup
     * and not the wait: the html is parsed and the deferred scripts have run, and no picture is
     * waited for. See [org.endy.pmczero.service.BrowserFetcher.render]
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
        @RequestParam(defaultValue = "true") persist: Boolean,
        @RequestParam(required = false) supplierId: String?,
        @RequestParam(defaultValue = "false") noDownload: Boolean,
        @RequestParam(defaultValue = "false") alwaysNewDownload: Boolean,
        @RequestParam(required = false) waitUntil: String?
    ): ImageImportTO = scraperImageImportService.importWithStoredScanner(
        scannerId = id,
        locationId = locationId,
        url = url,
        name = name,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = waitStateOf(waitForSelectorState),
        persist = persist,
        supplierId = supplierId,
        noDownload = noDownload,
        alwaysNewDownload = alwaysNewDownload,
        waitUntil = waitUntilOf(waitUntil)
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
