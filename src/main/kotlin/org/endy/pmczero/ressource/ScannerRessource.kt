package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.to.ScannerShortTO
import org.endy.pmczero.to.ScanningResultTO
import org.endy.pmczero.to.SerializedScannerTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/serializedscanners")
class ScannerRessource(
    val scannerService: ScannerService,
    val locationService: LocationService
) {

    @GetMapping("/{id}")
    fun getserializedScanner(@PathVariable id: Int): SerializedScannerTO {
        return scannerService.findById(id).toTO()
    }

    @GetMapping("/{id}/scan")
    fun scan(@PathVariable id: Int, @RequestParam url: String): ScanningResultTO {
        val mset = scannerService.scan(id, url)
        val urls = mset.media.flatMap { it.bessources.map { it.name ?: "" } }
        val (commonUrlStart, locations) = locationService.getLocationStartingWith(urls)
        return ScanningResultTO(mset.toTOwithMedia(true), commonUrlStart, locations.map { it.toTO() })
    }

    @PostMapping("/") //todo
    fun createserializedScanner(@RequestBody serializedScannerTO: SerializedScannerTO): SerializedScannerTO {
        if (serializedScannerTO.id != null) throw Exception()
        return scannerService.save(serializedScannerTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveserializedScanner(
        @PathVariable id: Int,
        @RequestBody serializedScannerTO: SerializedScannerTO
    ): SerializedScannerTO {
        if (id != serializedScannerTO.id) throw Exception()
        return scannerService.save(serializedScannerTO.toEntity()).toTO()
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


}
