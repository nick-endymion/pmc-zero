package org.endy.pmczero.ressource

import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.to.FileInfoDuplicateTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * What the files of this application are like, answered over http.
 *
 * Its own resource rather than a method on [MediumRessource] or [StorageRessource], because what it
 * answers is about no single medium and no single storage: a hash is a fact about content, and the same
 * file may sit on two storages or in two galleries, which is the whole reason a caller asks.
 */
@RestController
@RequestMapping("/api/file-infos")
class FileInfoRessource(private val fileInfoService: FileInfoService) {

    /**
     * Every content hash that more than one file of this application has, with the bessources of those
     * files, grouped by hash.
     *
     * The archive level question that nothing else here can answer: which files are the same file twice.
     * A scan writes its files and moves on, and nothing compares one file with another on the way in, so
     * a gallery imported twice, a copy somebody left in a second folder and a backup under another name
     * all look like separate files until their contents are hashed and compared. This is what compares
     * them.
     *
     * A group rather than a flat list, since the members of one are interchangeable by definition:
     * `[FileInfoDuplicateTO.bessources]` is what a caller acts on, keeping one of them and dropping the
     * rest.
     *
     * Only files that have been recorded are in here, so this answers for what has been looked at:
     * `POST /api/storages/{id}/set-fileinfos` is what makes an archive answer this at all, and a
     * storage it has not been run over is not free of duplicates as far as this goes, it is unexamined.
     *
     * A file without a hash is never a duplicate of anything, not even of another file without one,
     * since nothing is known about its content.
     *
     * Nothing is deleted by asking: which of a group to keep is a decision about a collection rather
     * than about a file, so this answers and leaves the rest to a caller.
     *
     * Answers an empty list for an archive with no duplicates, which is a successful answer rather than
     * an error: nothing has been looked at that says otherwise.
     */
    @Deprecated("Not used by the PMC-UI frontend")
    @GetMapping("/duplicates")
    fun duplicates(): List<FileInfoDuplicateTO> {
        return fileInfoService.duplicates()
    }
}