package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.repository.LocationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class LocationService(private val locationRepository: LocationRepository) {

    fun findById(id: Int): Location {
        return locationRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun url(bessource: Bessource, location: Location): String {
        return location.uri + "/" + bessource.name
    }

//    fun getLocationStartingWith(url: String): Location {
//        val locations = locationRepository.findLocationsByNameStartingWith(url)
//        if (locations.size != 1)
//            throw Exception()
//        return locations[0]
//    }

    fun getLocationStartingWith(urls: List<String>): List<Location> {
        val url = getCommonStart(urls)
        return locationRepository.findLocationsByNameStartingWith(url)
    }

    fun getCommonStart(urls: List<String>): String {
        if (urls.isEmpty()) throw Exception()
        if (urls.size == 1) return urls.get(0)
        var min = (urls.map { it.length }).minOrNull() ?: throw Exception()
        var result = ""
        outer_loop@ for (i in (0..min - 1)) {
            val url0 = urls.get(0)
            for (url in urls)
                if (url[i] != url0[i])
                    break@outer_loop
            result += urls.get(0)[i]
        }
        return result
    }

}
