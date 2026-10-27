package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.to.MediumTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Reports the media of a storage that hold a bessource naming the same file as another bessource of
 * the same storage.
 *
 * A duplicate is two rows of `a.bessources` with the same [org.endy.pmczero.model.modern.Bessource.name]
 * on the same storage, which is not a thing the database forbids and not a thing that can happen by
 * accident in one run: a scan that finds a file it already has skips it, see
 * [org.endy.pmczero.repository.BessourceRepository.findNamesOfExistingMedia]. It comes from a storage
 * that was imported twice, a medium whose file was moved onto a name that was taken already, or a
 * hand written row, and in each case nothing is wrong until somebody goes looking for the file and
 * finds two of them.
 *
 * Answers the media rather than the bessources, since a medium is what a caller decides something
 * about: which of the two keeps the file. Each medium carries all of its bessources with their
 * location type and url filled in, the same shape the media of a set and of a ranking are served in,
 * so the whole row behind the collision can be looked at without asking for every medium on its own.
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
    private val storageService: StorageService,
    private val mediaService: MediaService
) {

    /**
     * The media of [storageId] that hold a bessource sharing its name with another bessource of the
     * same storage, once per medium and in the order the colliding rows were answered in.
     *
     * A medium with several colliding rows is in the answer once, and a medium with one colliding row
     * is in it with all of its bessources, not only the colliding one. A name held by one row is not
     * in it at all.
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
    fun duplicatesIn(storageId: Int): List<MediumTO> {
        // resolved before the query rather than after, so an unknown storage is a 404 and not an
        // empty list that reads like "this storage is clean"
        storageService.findById(storageId)

        val duplicates = bessourceRepository.findDuplicatesOfStorage(storageId)

        // one entry per medium rather than per row: a medium may hold two of the colliding rows, and
        // the first one it appears with is where it stands in the order of the answer
        return duplicates
            .mapNotNull { it.medium }
//            .distinctBy { it.id }
            .map { mediaService.mediumTO(it) }
    }
}