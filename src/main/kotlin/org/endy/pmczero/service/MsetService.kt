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


    fun save(bset: Mset): Mset {
        return msetRepository.save(bset)
    }

    @Transactional
    fun delete(id: Int) {
        msetRepository.delete(findById(id))
        // TODO > what about media an their ressources ? This wont work
    }

    fun ressourcesInUse(id: Int): List<RessourceUrlsTO> {
        val media = findById(id).media
        val mips: MutableList<RessourceUrlsTO> = mutableListOf()
        for (medium in media) {
            try {  // important, since there are migrated media of type (legacy) folder, which have no ressources
                mips.add(
                    RessourceUrlsTO(
                        medium.id, medium.name,
                        mediaService.url(medium, RessType.PRIMARY),
                        mediaService.url(medium, RessType.TN)
                    ))
            } catch (e: Exception) {
                println("no ressource found for medium: " + id)
            }
        }
        return mips
    }

    fun htmlImagePage(id: Int, rtype: RessType): String {
        val media = findById(id).media

        var html = "<html><body>"
        for (medium in media) {
            try {  // important, since there are migrated media of type (legacy) folder, which have no ressources
                val url = mediaService.url(medium, rtype)
                val urlPrimary = mediaService.url(medium, RessType.PRIMARY)
                html += "<a href=\"" + urlPrimary + "\">   <img src=\"" + url + "\"></a>"
            } catch (e: Exception) {
                println("no ressource found for medium: " + id)
            }
        }
        return html + "</body></html>"

    }

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
