package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.LocationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.net.URI

@Service
class LocationService(
    private val locationRepository: LocationRepository,
    private val storageService: StorageService
) {

    val extension = ".jpg"

    fun findById(id: Int): Location {
        return locationRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun findAll(): List<Location> {
        return locationRepository.findAll().toList()
    }

    fun save(location: Location): Location {
        return locationRepository.save(location)
    }

    fun delete(id: Int) {
        locationRepository.delete(findById(id))
    }

    fun url(bessource: Bessource, location: Location): String {
        return location.uri + "/" +
                (bessource.name.takeIf { location.extension == null }
                    ?: bessource.name!!.replaceFirst("[.][^.]+$".toRegex(), "") + extension)
    }

//    fun getLocationStartingWith(url: String): Location {
//        val locations = locationRepository.findLocationsByNameStartingWith(url)
//        if (locations.size != 1)
//            throw Exception()
//        return locations[0]
//    }

    fun getLocationStartingWith(urls: List<String>): Pair<String, List<Location>> {
        val url = getCommonUrlStart(urls)
        return Pair(url, locationRepository.findLocationsByNameStartingWith(url))
    }

    fun getCommonUrlStart(urls: List<String>): String {
        val commonStart = getCommonStart(urls)
        val uri = URI(commonStart)
        return  uri.scheme+ "://"+ uri.getHost()
//        return commonStart.getBaseUrl() //todo
    }

    fun getCommonStart(urls: List<String>): String {
        if (urls.isEmpty()) return ""
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

    fun createDefaultLocationWithStorage(url: String): Location {
        val location = Location().also {
            it.uri = url
            it.name = url
            it.locationType = 1;
        }
        val storage = Storage().also {
            it.name = url
        }
        storageService.save(storage)
        location.storage = storage
        save(location)
        return location
//        storage.locations = listOf(location)

    }

}
