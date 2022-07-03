package org.endy.pmczero.model.scraper

import kotlinx.serialization.*
import kotlinx.serialization.json.*

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.springframework.stereotype.Service

@Service
class Scraper(val locationService: LocationService, val downloader: Downloader) {

    lateinit var scanner: Scanner

    init {
        // Scannergroup
        loadScanners()
    }

    fun scan(url: String, locationId: Int? = null): ScanningKontext {
//        val location = locationService.getLocationStartingWith(url)
        val location = if (locationId == null)
            Location().also { it.name = "Catchup Location"; it.uri = "" }
        else
            locationService.findById(locationId)
        val sc = getNewScanningContext(location)
        scanner.doWork(url, "", sc)
        return sc
    }

    fun getNewScanningContext(location: Location): ScanningKontext {
        return ScanningKontext(location, BSet(), arrayListOf(), downloader)
    }

    fun serialize() {
        println(Json.encodeToString(scanner))

    }

    fun loadScanners() {

        scanner = Scanner(
            SimpleParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scanner(DomParser("(.*)", "title", ""), SetCreator()),
                    Scanner(
                        DomParser("(.*)", "a", ""), MediaAdder()
                    )
                )
            )
        )
    }
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

