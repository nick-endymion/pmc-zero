package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toDuplicateNameTO
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.to.DuplicateNameBessourceTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Reports the bessources of a storage that name the same file twice.
 *
 * A duplicate is two rows of `a.bessources` with the same [org.endy.pmczero.model.modern.Bessource.name]
 * on the same storage, which is not a thing the database forbids and not a thing that can happen by
 * accident in one run: a scan that finds a file it already has skips it, see
 * [org.endy.pmczero.repository.BessourceRepository.findNamesOfExistingMedia]. It comes from a storage
 * that was imported twice, a medium whose file was moved onto a name that was taken already, or a
 * hand written row, and in each case nothing is wrong until somebody goes looking for the file and
 * finds two of them.
 *
 * Not the same report as [FileInfoService.duplicates], which groups by content hash over the whole
 * database: that one says which files are the same bytes, this one says which two rows point at one
 * file. Both are asked, since neither can be derived from the other without hashing every file on the
 * way in.
 *
 * Nothing is changed here. Finding out what to do about a collision is a decision about which medium
 * keeps the file, and it is made by a person looking at the answer of [duplicatesIn], not by a rule
 * about which row is older.
 *
 * Scoped to one storage rather than to the whole database, so the answer is bounded by a folder
 * rather than by everything ever scanned, and so the same name on two storages is two files that are
 * not in conflict. A caller that wants the whole picture asks one storage at a time.
 */
@Service
class DuplicateBessourceService(
    private val bessourceRepository: BessourceRepository,
    private val storageService: StorageService
) {

    /**
     * The bessources of [storageId] that share a name with another bessource of the same storage,
     * as one entry per row, ordered by name and id.
     *
     * Every member of a colliding group is in the answer, so a name held by three rows comes back
     * three times with a [DuplicateNameBessourceTO.duplicates] of 3, and a name held by one row is
     * not in it at all.
     *
     * Only the rows of a medium that is not marked deleted are reported, see
     * [org.endy.pmczero.model.modern.Medium.deleted]: a marked medium is not acted on any more, so
     * its files are unknown to a scan again and its rows are not a collision anybody is going to
     * resolve. A bessource without a medium is left out for the same reason, since there is nothing
     * behind it that a caller could decide to keep.
     *
     * Read only, and in one transaction: the grouping is left to the query, so the medium and the
     * storage of a row are fetched along with it rather than left lazy, and the mapping below has to
     * happen while the session is still open.
     *
     * @throws NotFoundException when no storage with that id exists, so a mistyped id is not
     * mistaken for a storage without duplicates
     */
    @Transactional(readOnly = true)
    fun duplicatesIn(storageId: Int): List<DuplicateNameBessourceTO> {
        // resolved before the query rather than after, so an unknown storage is a 404 and not an
        // empty list that reads like "this storage is clean"
        storageService.findById(storageId)

        val duplicates = bessourceRepository.findDuplicatesOfStorage(storageId)

        // how many rows share each name, counted once over the whole answer rather than per row: the
        // size of a group is a fact of the storage, not of the row that happens to be mapped first
        val sizes = duplicates.groupingBy { it.name }.eachCount()

        return duplicates.map { it.toDuplicateNameTO(sizes.getValue(it.name)) }
    }
}