package org.endy.pmczero.service

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.Scraper
import org.springframework.stereotype.Service

@Service
class ScraperService(
    val locationService: LocationService,
    val downloader: Downloader,
    val browserFetcher: BrowserFetcher
) {

//    lateinit var scanner: Scanner
    lateinit var scrapers: List<Scraper>

    init {
        // Scannergroup
//        loadScanners()
    }

    fun scan(scraper: Scraper, url: String, locationId: Int? = null): ScanningKontext =
        scanWith(scraper, url, locationId, downloader)

    /**
     * The placeholder location a scan that has not been assigned a real one yet runs against.
     *
     * Carries an empty uri and a fresh storage, so [MediaAdder] takes the whole url as the name of a
     * bessource and [changeToRealLocation] can still tell that nothing real has been assigned yet.
     */
    fun catchupLocation(): Location =
        Location().also { it.name = "Catchup Location"; it.uri = ""; it.storage = Storage() }

    /**
     * The same scan as [scan], with every page read by the browser instead of by plain http.
     *
     * Nothing else differs: the parsers and the workers are handed the same rendered html, so a page
     * that fills itself in with javascript yields the same elements as a static one. Which fetcher a
     * scan runs on is a property of the scan rather than of the scraper, so one scraper definition
     * serves both.
     */
    fun scanWithBrowser(scraper: Scraper, url: String, locationId: Int? = null): ScanningKontext =
        scanWith(scraper, url, locationId, browserFetcher)

    private fun scanWith(
        scraper: Scraper,
        url: String,
        locationId: Int?,
        fetcher: Fetcher
    ): ScanningKontext {
//        val location = locationService.getLocationStartingWith(url)
        val location = if (locationId == null) catchupLocation() else locationService.findById(locationId)
        val sc = getNewScanningContext(location, fetcher)
        scraper.doWork(url, "", sc)
        return sc
    }

    fun changeToRealLocation(sc: ScanningKontext, location: Location) {
        check(location.id != null)
        check(sc.location.name == "")
        check(sc.location.id == null)
        check(sc.mset != null)

        for (media in sc.mset!!.media) {
            for (b in media.bessources) {
                b.name = location.getRightPart(b.name!!)
                b.storage = location.storage
            }
        }
    }

    fun getNewScanningContext(location: Location, fetcher: Fetcher = downloader): ScanningKontext {
        return ScanningKontext(location, Mset(), arrayListOf(), fetcher)
    }

    fun findPossibleLocations(sc: ScanningKontext): List<Location> {
        val aaa = sc.mset!!.media.map { it.bessources }.flatten().map { it.name ?: "" }
        return locationService.getLocationStartingWith(aaa).second
    }


//    fun findScanner(text: String) : List<Scanner> {
//    }

//    fun serialize() {
//        println(Json.encodeToString(scanner))
//
//    }

//    fun loadScanners() {
//
//        // load all from DB
//        // todo
//        scanner = Scanner(
//            RegexParser("(.*fa.*)"),
//            StructuredWorker(
//                true,
//                listOf(
//                    Scanner(DomParser("(.*)", "title", ""), SetCreator()),
//                    Scanner(
//                        DomParser("(.*)", "a", ""), MediaAdder()
//                    )
//                )
//            )
//        )
//    }
}


//    scanner:
//          simpleparser: (.Üafa*)
//          structuredWorker:
//                download: true
//                Scanner:
//                  - Domparser:
//                          regex: ..
//                          tag: ...
//                          attribute: ...
//                    SetCreator
//                  - DomParser
//                    MediaCreator
