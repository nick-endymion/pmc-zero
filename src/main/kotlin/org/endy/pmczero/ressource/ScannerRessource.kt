package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.SerializedScannerService
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.ScannerShortTO
import org.endy.pmczero.to.SerializedScannerTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/serializedscanners")
class ScannerRessource(
    val serializedScannerService: SerializedScannerService
) {

    @GetMapping("/{id}")
    fun getserializedScanner(@PathVariable id: Int): SerializedScannerTO {
        return serializedScannerService.findById(id).toTO()
    }

    @GetMapping("/{id}/scan")
    fun scan(@PathVariable id: Int, @RequestParam url: String): MsetTO {
        return serializedScannerService.scan(id, url)
    }

    @PostMapping("/") //todo
    fun createserializedScanner(@RequestBody serializedScannerTO: SerializedScannerTO): SerializedScannerTO {
        if (serializedScannerTO.id != null) throw Exception()
        return serializedScannerService.save(serializedScannerTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveserializedScanner(@PathVariable id: Int, @RequestBody serializedScannerTO: SerializedScannerTO): SerializedScannerTO {
        if (id != serializedScannerTO.id) throw Exception()
        return serializedScannerService.save(serializedScannerTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteserializedScanner(@PathVariable id: Int) {
        return serializedScannerService.delete(id)
    }

//    @PostMapping("/bla")
//    fun XcreateserializedScanner(@RequestBody serializedScanner: SerializedScannerTO) : SerializedScannerTO {
////        if (serializedScannerTO.id != null) throw Exception()
//        return serializedScannerService.DeserializeAndSerialize(serializedScanner.toEntity()).toTO()
//    }

    @GetMapping("/")
    fun getserializedScanner(@RequestParam searchTerm: String, @RequestParam id: Int?): List<ScannerShortTO> {
        return serializedScannerService.findByUrl(searchTerm).map { it.toTO() }
    }


}
