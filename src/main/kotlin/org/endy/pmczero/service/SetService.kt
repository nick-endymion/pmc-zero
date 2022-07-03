package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.SetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.endy.pmczero.model.modern.BSet

@Service
class SetService(
    private val mediaRepository: MediaRepository,
    private val setRepository: SetRepository,
    private val mediaService: MediaService
) {

    fun findById(id: Int): BSet {
        return setRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun save(bset: BSet): BSet {
        return setRepository.save(bset)
    }

    fun htmlImagePage(id: Int, rtype: RessType): String {
        val media = findById(id).media

        var html = "<html><body>"
        for (medium in media) {
            val url = mediaService.url(medium.id!!, rtype)
            html += "<img src=\"" + url + "\">"
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
