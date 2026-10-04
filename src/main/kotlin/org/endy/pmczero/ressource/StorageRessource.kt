package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.StorageTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storages")
class StorageRessource(
    val storageService: StorageService
) {

    @GetMapping("/")
    fun getStorages(): List<StorageTO> {
        return storageService.findAll().map { it.toTO() }
    }

    @GetMapping("/{id}")
    fun getStorage(@PathVariable id: Int): StorageTO {
        return storageService.findById(id).toTO()
    }

    @GetMapping("/{id}/locations")
    fun getStorageWithLocations(@PathVariable id: Int): StorageTO {
        return storageService.findById(id, withLocations = true).toTO(withLocations = true)
    }

    @PostMapping("/")
    fun createStorage(@RequestBody storageTO: StorageTO): StorageTO {
        if (storageTO.id != null) throw Exception()
        val saved = storageService.save(storageTO.toEntity())
        // re-read so the response reflects the persisted row
        return storageService.findById(saved.id!!).toTO()
    }

    @PutMapping("/{id}")
    fun saveStorage(@PathVariable id: Int, @RequestBody storageTO: StorageTO): StorageTO {
        if (id != storageTO.id) throw Exception()
        storageService.save(storageTO.toEntity())
        // re-read: createdAt is updatable=false, so the merged instance does not carry it back
        return storageService.findById(id).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteStorage(@PathVariable id: Int) {
        return storageService.delete(id)
    }

}
