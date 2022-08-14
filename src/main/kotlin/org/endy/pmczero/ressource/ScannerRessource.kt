package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.to.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scanners")
class ScannerRessource(
    val scannerService: ScannerService,
    val locationService: LocationService,
    val msetService: MsetService
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
    fun getserializedScanner(@RequestParam searchTerm: String, @RequestParam id: Int?): List<ScannerShortTO> {
        return scannerService.findByUrl(searchTerm).map { it.toTO() }
    }



    @GetMapping("/{id}/scan")
    fun scan(@PathVariable id: Int, @RequestParam url: String): ScanningResultTO {
        val mset = scannerService.scan(id, url)
        val urls = mset.media.flatMap { it.bessources.map { it.name ?: "" } }
        val (commonUrlStart, locations) = locationService.getLocationStartingWith(urls)
        return ScanningResultTO(mset.toTOwithMedia(true), commonUrlStart, locations.map { it.toTO() })
    }
    @PostMapping("/{id}/scan")
    fun scan(@RequestBody sts: SourceToScanTO): ScanningResultTO {
        if (sts.scannerId == null) throw Exception()
        var mset = scannerService.scan(sts)
        if (sts.persist == true)
            mset = msetService.save(mset)
        return ScanningResultTO(mset.toTOwithMedia(true), "", listOf( locationService.findById(sts.locationId!!).toTO()))
    }

}
