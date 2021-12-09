package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.MfilesEntity
import org.endy.pmczero.model.RessType
import org.endy.pmczero.repository.MfilesRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MfileService(
    private val mfilesRepository: MfilesRepository,
    private val locationService: LocationService
) {

    fun findById(id: Int): MfilesEntity {
        return mfilesRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun url(id: Int, type: RessType): String {

        val mfile = findById(id)

        val storage = mfile.folder?.storage

        if (storage != null) {
            println(storage.id)
            println(storage.locationsInuse)
//            val location = storage.locationsInuse.first { loc -> loc.typ == 1 }
            val locations = storage.locations.filter { loc -> loc.inuse == 1.toByte() }
            val location = locations.first { loc -> loc.typ == 1 }
            return locationService.url(mfile, location)
        }
        return "N/A"
    }

    fun file(id: Int, type: RessType) {

    }
}
