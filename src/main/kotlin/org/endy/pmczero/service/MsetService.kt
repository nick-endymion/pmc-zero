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
import org.endy.pmczero.to.MsetScanTO
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.transaction.annotation.Transactional

@Service
class MsetService(
    private val mediaRepository: MediaRepository,
    private val msetRepository: MsetRepository,
    private val mediaService: MediaService,
    private val storageService: StorageService,
    private val locationService: LocationService
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
     */
    @Transactional
    fun delete(id: Int) {
        msetRepository.delete(findById(id))
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
