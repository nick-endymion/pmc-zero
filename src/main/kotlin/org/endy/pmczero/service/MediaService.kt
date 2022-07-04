package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.MediaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MediaService(
    private val mediaRepository: MediaRepository,
    private val bessourceRepository: BessourceRepository,
    private val locationService: LocationService
) {

    fun findById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun save(medium: Medium): Medium {
        return mediaRepository.save(medium)
    }

    fun findBesById(id: Int): Bessource {
        return bessourceRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun url(id: Int, type: RessType): String {
        val medium = findById(id)
        val rt = if (type == RessType.TN)
            RessType.PRIMARY
        else
            type

        val bessource = medium.bessources.first { it.btype == rt.i }
        val storage = bessource.storage
        val location = storage.locationInUse(if (type == RessType.TN) 3 else 1)
        if (location != null)
            return locationService.url(bessource, location)

        if (type == RessType.TN) {
            val bessource = medium.bessources.first { type == RessType.TN }
            val storage = bessource.storage
            val location = storage.locationInUse(1)
            if (location != null)
                return locationService.url(bessource, location)
        }
        return "N/A"
    }

    fun file(id: Int, type: RessType) {

    }

    fun mediumById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun urlByMediumId(id: Int, type: RessType): String {
        val medium = mediumById(id)
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
