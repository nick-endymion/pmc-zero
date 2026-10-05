package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.transaction.annotation.Transactional

@Service
class MsetService(
    private val mediaRepository: MediaRepository,
    private val msetRepository: MsetRepository,
    private val mediaService: MediaService
) {

    fun findById(id: Int, withMedia: Boolean = false): Mset {
        if (withMedia)
            return msetRepository.findByIdOrNullWithMedia(id) ?: throw NotFoundException()
        else
            return msetRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun search(searchTerm: String): List<Mset> {
        return msetRepository.findAllByNameContaining(searchTerm)
    }

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
