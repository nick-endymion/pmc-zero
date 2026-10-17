package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.endy.pmczero.mapper.toScanTO
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.to.MsetDeletionTO
import org.endy.pmczero.to.MsetScanTO
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.transaction.annotation.Transactional

@Service
class MsetService(
    private val mediaRepository: MediaRepository,
    private val msetRepository: MsetRepository,
    private val mediaService: MediaService,
    private val storageService: StorageService,
    private val locationService: LocationService,
    private val mediaDeletionService: MediaDeletionService
) {

    /**
     * the msets that hold media whose files live on [storageId]
     *
     * An mset has no storage of its own, so this walks set -> media -> bessource -> storage: a set
     * belongs to the storage its files are on. See [MsetRepository.findByStorageId].
     *
     * @throws NotFoundException when no storage with that id exists. An unknown id is answered as
     * such rather than as a storage that happens to hold nothing, so a mistyped id does not read
     * as an empty result
     */
    fun findByStorageId(storageId: Int): List<Mset> {
        storageService.findById(storageId)
        return msetRepository.findByStorageId(storageId)
    }

    /**
     * The sets other than the one with [msetId] that hold at least one file it holds as well, in id
     * order. See [MsetRepository.findSetsSharingFilesWith].
     *
     * Sets related by what they contain rather than by anything about them: two sets whose bessources
     * name the same file on the same storage, which is what an archive copied onto a second medium or
     * a gallery imported twice leaves behind. Which files those are is not in the answer, since the
     * caller asked for the sets; `GET /api/storages/{id}/duplicate-bessources` is where a name held
     * twice on one storage is answered.
     *
     * Empty rather than an error for a set that shares nothing, which is the normal state of a set
     * whose gallery was imported once.
     *
     * @throws NotFoundException when no mset with that id exists. Read before the query rather than
     * after it, since the query would answer an empty list for a set that is not there, and a mistyped
     * id must not read as "this set shares nothing with anybody"
     */
    fun findSetsSharingFilesWith(msetId: Int): List<Mset> {
        findById(msetId)
        return msetRepository.findSetsSharingFilesWith(msetId)
    }

    fun findById(id: Int, withMedia: Boolean = false): Mset {
        if (withMedia)
            return msetRepository.findByIdOrNullWithMedia(id) ?: throw NotFoundException()
        else
            return msetRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun search(searchTerm: String): List<Mset> {
        return msetRepository.findAllByNameContaining(searchTerm)
    }

    /**
     * The sets carrying exactly [tag], in id order. See [MsetRepository.findAllByTag].
     *
     * Empty rather than an error for a tag no set carries, which is a result: a tag nobody has used is a
     * tag a caller is about to use, and a 404 here would read as though the tag itself were wrong.
     */
    fun searchByTag(tag: String): List<Mset> {
        return msetRepository.findAllByTag(tag)
    }

    /**
     * Every tag in use across the sets, alphabetically. See [MsetRepository.findAllTags].
     *
     * Empty rather than an error when nothing is tagged yet, since a database that has no tags is a
     * database nobody has tagged and not a missing table.
     */
    fun allTags(): List<String> {
        return msetRepository.findAllTags()
    }

    /**
     * The sets that were imported from [url], in id order. See
     * [MsetRepository.findAllByUrlOrderById].
     *
     * A list and not a single set, since a page may well have been imported more than once: once with
     * its files, and again with `noDownload` to record what it holds without fetching anything, or for
     * a second supplier of the same gallery. Which of them holds what is then a question about the
     * individual sets, see [Mset.supplierId] and [Mset.scannnerId].
     *
     * Empty rather than an error when nothing was imported from that url, which is a result and not a
     * failure: a page that has never been scraped is a page a caller is about to scrape, and a 404 here
     * would read as though the url were wrong.
     */
    fun findByUrl(url: String): List<Mset> = msetRepository.findAllByUrlOrderById(url)

    /**
     * The mset following the one with [id] in id order, i.e. the one with the smallest id greater
     * than it. See [MsetRepository.findFirstAboveId].
     *
     * For stepping through the sets one at a time, rather than for finding one by name: it says
     * nothing about [Mset.name] or [Mset.subpath], so the order it walks is the order the rows were
     * created in. That is the only order a set has of its own.
     *
     * The id handed in is not required to name a set. A caller asking for the set after a deleted one
     * still gets the set that follows it numerically, which is what a list that is one row out of date
     * needs.
     *
     * @throws NotFoundException when no mset has a greater id, i.e. this is the last one. There is no
     * set above it to answer, and answering something else would leave a caller unable to tell the
     * end of the collection from a set it had already seen
     */
    fun findAbove(id: Int): Mset =
        msetRepository.findFirstAboveId(id) ?: throw NotFoundException()

    /**
     * The mset preceding the one with [id] in id order, i.e. the one with the largest id smaller
     * than it. See [findAbove], of which this is the counterpart in the other direction.
     *
     * @throws NotFoundException when no mset has a smaller id, i.e. this is the first one
     */
    fun findBelow(id: Int): Mset =
        msetRepository.findFirstBelowId(id) ?: throw NotFoundException()

    /**
     * Saves [mset], with its media attached to it and their bessources to them.
     *
     * Transactional, which the walk below needs: a set that was read somewhere else carries media whose
     * bessources are still lazy, and touching one of those outside a session fails rather than loading
     * it. A set that was read with its media and is saved again is exactly that case, since the media of
     * a second run are added to the set the first run left rather than to a set of its own.
     *
     * A set that has an id is merged rather than inserted, so this is also how a scan adds to a set that
     * is already there: the media of the run are added to the ones the set already holds.
     */
    @Transactional
    fun save(mset: Mset): Mset {
        mset.media.forEach {
            it.mset = mset
            it.bessources.forEach { b ->
                b.medium = it
            }
        }
        return msetRepository.save(mset)
    }

    /**
     * Adds the files below [subdir] of the location with [locationId] to the mset with [msetId] that
     * have no medium yet, answering what was added and the set as it was saved.
     *
     * The counterpart of [LocationService.draftMset] for a set that exists already: the same scan, the
     * same rules about which files count, but into that set rather than into a new one. So a set whose
     * directory has grown picks the new files up without producing a second set for the same directory,
     * which is what scanning it again through the draft endpoint would do.
     *
     * [subdir] defaults to the subpath the set already records, see [Mset.subpath]: a set knows the
     * directory it was built from, so a caller expanding it need not repeat that. An explicit [subdir]
     * wins, which is what makes this usable for a set whose directory moved.
     *
     * Nothing new is answered rather than refused, since that is the normal state of a directory that
     * has not changed and repeating the call has to stay harmless.
     *
     * The media that were already there are left untouched, and so is the [Mset.locationId] of the set:
     * it is where the set came from, not where it is being extended to.
     *
     * @throws NotFoundException when no mset with that id exists
     * @throws NotAccessibleException when the location is not an accessible FS location, or has no
     * storage and there is something to add
     * @throws org.endy.pmczero.exception.NotFoundException when [subdir] does not exist or points
     * outside of the location
     */
    @Transactional
    fun expandMset(msetId: Int, locationId: Int, subdir: String? = null): MsetScanTO {
        val mset = findById(msetId, withMedia = true)
        // the set's own directory when the caller names none, which is why it is read before the scan:
        // the scan needs the subpath, and there is no call afterwards that could tell what was used
        val scanned = subdir?.takeIf { it.isNotBlank() } ?: mset.subpath

        val scan = locationService.expandMset(mset, locationId, scanned)

        return save(scan.mset).toScanTO(scan.addedFiles, scan.knownFiles)
    }

    /**
     * Deletes the mset together with its media and the bessources of those media, which is what the
     * cascades of [Mset.media] and [Medium.bessources] take care of.
     *
     * A medium that carries bookmarks is the one exception: [Medium.bookmarks] has no cascade, so
     * the delete of such a set is refused by the database and rolled back as a whole.
     *
     * The files of the media are left where they are. [deleteIncludingMarkedFiles] is the one that
     * moves the files of the media somebody marked deleted.
     */
    @Transactional
    fun delete(id: Int) {
        msetRepository.delete(findById(id))
    }

    /**
     * Deletes the mset as [delete] does, and before that the files of every one of its media that is
     * marked deleted, moving them below the `DELETED` folder of their location, answering what each
     * of them did.
     *
     * The two steps in that order, and it is not an arbitrary one: deleting the set cascades to the
     * bessources of its media, and a name that is gone from the database is a file that can no longer
     * be found, so the files have to be moved while the bessources still say where they are.
     *
     * Only the marked media are treated this way. A media that was not marked goes with the set as a
     * row and its files stay where they are, which is what somebody who never tagged them asked for.
     *
     * The set is read before anything is deleted rather than afterwards, so an id that names no set
     * is a 404 without a file having been moved for it.
     *
     * A medium that carries bookmarks is still refused, see [delete]: the delete of the set rolls
     * back, and with it the rows of the media that were deleted, while the files that had already
     * been moved stay below the `DELETED` folder. That is the price of a file move not being
     * transactional, the same one [MediaDeletionService.deleteMarkedMedium] pays.
     *
     * @throws NotFoundException when no mset with that id exists
     * @throws org.endy.pmczero.exception.NotAccessibleException when a file to be moved is already
     * there under its new name
     */
    @Transactional
    fun deleteIncludingMarkedFiles(id: Int): MsetDeletionTO {
        val mset = findById(id)
        val marked = mediaDeletionService.deleteMarkedMediaOfMset(id)
        // read again rather than deleting the instance above: the media collection of a set is lazy
        // and is loaded when the cascade walks it, which is after the deletions above, so it holds the
        // rows that are left rather than the ones that were already removed
        delete(id)

        return MsetDeletionTO(msetId = id, name = mset.name, media = marked)
    }

    fun ressourcesInUse(id: Int): List<RessourceUrlsTO> {
        val media = findById(id).media
        val mips: MutableList<RessourceUrlsTO> = mutableListOf()
        for (medium in media) {
            try {  // important, since there are migrated media of type (legacy) folder, which have no ressources
                val ressourceUrls = mediaService.ressourceUrls(medium)
                if (ressourceUrls == null) {
                    println("No ressource found for medium: " + medium.id)
                    continue
                }
                mips.add(ressourceUrls)
            } catch (e: Exception) {
                println("Excpetion: no ressource found for medium: " + id)
            }
        }
        return mips
    }

//    fun htmlImagePage(id: Int, rtype: RessType): String {
//        val media = findById(id).media
//
//        var html = "<html><body>"
//        for (medium in media) {
//            try {  // important, since there are migrated media of type (legacy) folder, which have no ressources
//                val url = mediaService.url(medium, rtype)
//                val urlPrimary = mediaService.url(medium, RessType.PRIMARY)
//                html += "<a href=\"" + urlPrimary + "\">   <img src=\"" + url + "\"></a>"
//            } catch (e: Exception) {
//                println("no ressource found for medium: " + id)
//            }
//        }
//        return html + "</body></html>"
//
//    }

    fun url(id: Int, type: RessType): String {
//        val mfile = findById(id)
//        val storage = mfile.folder?.storage
//        if (storage != null) {
//            println(storage.id)
//            println(storage.locationsInuse)
//            val locations = storage.locations.filter { loc -> loc.inuse == 1.toByte() }
//            val location = locations.first { loc -> loc.typ == 1 }
//            return locationService.url(mfile, location)
//        }
        return "N/A"
    }

    fun file(id: Int, type: RessType) {

    }

//

    fun mediumById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }


    fun addMedium(mset: Mset, medium: Medium) {
        medium.mset = mset
        mediaService.save(medium)
    }

    fun urlByMediumId(id: Int, type: RessType): String {
//        val medium = mediumById(id)
//        val mfile =  medium.mfiles.first() // Todo > filter logic by resstype
//        val storage = mfile.folder?.storage
//        if (storage != null) {
//            val locations = storage.locations.filter { loc -> loc.inuse == 1.toByte() }
//            val location = locations.first { loc -> loc.typ == 1 }
//            return locationService.url(mfile, location)
//        }
        return "N/A"
    }

}
