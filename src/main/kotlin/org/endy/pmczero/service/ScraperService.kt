package org.endy.pmczero.service

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.Scraper
import org.springframework.stereotype.Service

@Service
class ScraperService(val locationService: LocationService, val downloader: Downloader) {

//    lateinit var scanner: Scanner
    lateinit var scrapers: List<Scraper>

    init {
        // Scannergroup
//        loadScanners()
    }

    fun scan(scraper: Scraper, url: String, locationId: Int? = null): ScanningKontext {
//        val location = locationService.getLocationStartingWith(url)
        val location = if (locationId == null)
            Location().also { it.name = "Catchup Location"; it.uri = ""; it.storage = Storage() }
        else
            locationService.findById(locationId)
        val sc = getNewScanningContext(location)
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

    fun getNewScanningContext(location: Location): ScanningKontext {
        return ScanningKontext(location, Mset(), arrayListOf(), downloader)
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
//         // todo
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


//        scanner:
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

