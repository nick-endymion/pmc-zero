package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.StorageTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storages")
class StorageRessource(
    val storageService: StorageService,
    val msetService: MsetService
) {

    @GetMapping("/")
    fun getStorages(): List<StorageTO> {
        return storageService.findAll().map { it.toTO(true) }
    }

    @GetMapping("/{id}")
    fun getStorage(@PathVariable id: Int): StorageTO {
        return storageService.findById(id).toTO(true)
    }

    @GetMapping("/{id}/locations")
    fun getStorageWithLocations(@PathVariable id: Int): StorageTO {
        return storageService.findById(id, withLocations = true).toTO(withLocations = true)
    }

    /**
     * Answers the msets whose media point at files on this storage.
     *
     * An mset has no storage of its own, so the relation runs through its media and their
     * bessources. The media are left out of the answer: this is the list of sets, and a caller
     * that wants the files of a set asks for it on its own url.
     *
     * Answers 404 for an unknown storage, so a mistyped id is not mistaken for a storage without
     * sets.
     */
    @GetMapping("/{id}/msets")
    fun getMsets(@PathVariable id: Int): List<MsetTO> {
        return msetService.findByStorageId(id).map { it.toTO() }
    }

    @PostMapping("/")
    fun createStorage(@RequestBody storageTO: StorageTO): StorageTO {
        val saved = storageService.save(storageTO.toEntity())
        // re-read so the response reflects the persisted row
        return storageService.findById(saved.id!!).toTO(true)
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
