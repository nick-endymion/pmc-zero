package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.FileInfoRunTO
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.StorageTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storages")
class StorageRessource(
    val storageService: StorageService,
    val msetService: MsetService,
    val fileInfoService: FileInfoService
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

    /**
     * Records what is missing of this storage: the file behind every bessource of it that has no
     * [org.endy.pmczero.model.modern.FileInfo] yet, and answers what the run did.
     *
     * A bessource that has a row is left alone, so calling this a second time over a storage that was
     * recorded a moment ago writes nothing and costs one query, while calling it after a scan that
     * brought in new files fills in the new ones and leaves the old ones as they are.
     *
     * Every file is hashed, so this is a call that takes as long as the storage is large and reads
     * every file on it once. The writes land a hundred at a time rather than in one transaction at the
     * end, so a run that is interrupted has recorded the first hundred files and the next hundred and
     * not the last one, and each of those is worth keeping.
     *
     * A file that cannot be read is reported as a failure and does not stop the run, so the answer
     * carries [org.endy.pmczero.to.FileInfoRunTO.attempted], `recorded`, `skipped` and `failed` rather
     * than being the recorded ones.
     *
     * Answers 404 for an unknown storage, so a mistyped id is not mistaken for a storage with nothing
     * to record.
     */
    @PostMapping("/{id}/set-fileinfos")
    fun setFileInfos(@PathVariable id: Int): FileInfoRunTO {
        // so an unknown storage is a 404 rather than a run over nothing that reports success
        storageService.findById(id)

        return fileInfoService.recordMissingOf(id)
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
