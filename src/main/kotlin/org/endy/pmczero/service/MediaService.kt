package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LegacyMedium
import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MfilesRepository
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

    fun findBesById(id: Int): Bessource {
        return bessourceRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun url(id: Int, type: RessType): String {
        val medium = findById(id)
        val bessource =  medium.bessources.first { it.btype == null } //TODO

        val storage = bessource.storage
        if (storage != null) {
            println(storage.id)
//            println(storage.locationsInuse)
            val locations = storage.locations.filter { loc -> loc.inuse == 1.toByte() }
            val location = locations.first { loc -> loc.typ == 1 }
            return locationService.url(bessource, location)
        }
        return "N/A"
    }

    fun file(id: Int, type: RessType) {

    }

    fun mediumById(id: Int): Medium {
        return  mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
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
