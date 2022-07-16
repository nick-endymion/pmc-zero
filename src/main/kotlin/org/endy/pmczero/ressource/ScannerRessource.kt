package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.SerializedScannerService
import org.endy.pmczero.to.SerializedScannerTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/serializedscanners")
class ScannerRessource(
    val serializedScannerService: SerializedScannerService
) {


    @PostMapping("/")
    fun createMset(@RequestBody serializedScanner: SerializedScannerTO) : SerializedScannerTO {
//        if (msetTO.id != null) throw Exception()
        return serializedScannerService.deserialize(serializedScanner.toEntity()).toTO()
    }
}
