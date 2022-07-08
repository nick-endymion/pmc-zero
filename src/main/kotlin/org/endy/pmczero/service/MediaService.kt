package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
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

    fun url(id: Int, ressType: RessType): String {
        val medium = findById(id)
        var ressTypeFirstAttempt = ressType.takeUnless { ressType == RessType.TN } ?: RessType.PRIMARY

        val url = getUrlFor(medium,
            ressType.takeUnless { ressType == RessType.TN } ?: RessType.PRIMARY,
            LocationType.MAIN_HTTP.takeUnless { ressType == RessType.TN } ?: LocationType.TN_HTTP)

        if (url != null) return url

        if (ressType != RessType.TN) {
            throw Exception()
        }
        return getUrlFor(
            medium,
            ressType,
            LocationType.MAIN_HTTP
        ) ?: throw Exception()
    }

    fun getUrlFor(medium: Medium, ressType: RessType, locationType: LocationType): String? {
        val bessource = medium.bessources.first { it.ressType == ressType.i }
        val storage = bessource.storage
        val location = storage.locationInUse(locationType.i)
        if (location == null) return null
        return locationService.url(bessource, location)
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
