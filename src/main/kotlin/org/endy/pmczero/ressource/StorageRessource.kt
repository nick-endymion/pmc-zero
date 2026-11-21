package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.DuplicateBessourceService
import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.FileInfoRunTO
import org.endy.pmczero.to.MediumTO
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.StorageTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storages")
class StorageRessource(
    val storageService: StorageService,
    val msetService: MsetService,
    val fileInfoService: FileInfoService,
    val duplicateBessourceService: DuplicateBessourceService
) {

    @GetMapping("/")
    fun getStorages(): List<StorageTO> {
        return storageService.findAll().map { it.toTO(true) }
    }

    @GetMapping("/{id}")
    fun getStorage(@PathVariable id: Int): StorageTO {
        return storageService.findById(id).toTO(true)
    }

    @Deprecated("Not used by the PMC-UI frontend")
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
     * The media of this storage that hold a bessource naming the same file as another bessource of
     * the same storage, once per medium.
     *
     * Two rows of the same name on one storage are a collision: the file they point at is the same,
     * so only one of them can be the record of it. Which one is a decision about the medium behind
     * it, which is why the media are answered rather than one row being picked here. Every medium
     * comes with all of its bessources, with location type and url filled in, so a caller can see
     * the whole row behind the collision without asking for it on its own.
     *
     * Its own url rather than a query on this one, since the answer is bounded by the storage rather
     * than by the storage and what else a caller asks for at the same time.
     *
     * The same name on two storages is not a duplicate and is not answered here, since those are two
     * files that happen to be called the same. A caller after the whole picture asks one storage at
     * a time.
     *
     * Not the same report as `/api/file-infos/duplicates`, which groups by content hash over the
     * whole database: that one says which files are the same bytes, this one says which two rows
     * point at one file. A caller after either asks for its own url.
     *
     * Nothing is changed: this is a report, and working out what to do about a collision is made by
     * whoever reads it.
     *
     * Answers 404 for an unknown storage, so a mistyped id is not mistaken for a storage without
     * duplicates.
     */
    @GetMapping("/{id}/duplicate-bessources")
    fun getDuplicateBessources(@PathVariable id: Int): List<MediumTO> {
        return duplicateBessourceService.duplicatesIn(id)
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
    @Deprecated("Not used by the PMC-UI frontend")
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

    @Deprecated("Not used by the PMC-UI frontend")
    @DeleteMapping("/{id}")
    fun deleteStorage(@PathVariable id: Int) {
        return storageService.delete(id)
    }

}
