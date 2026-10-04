package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.to.BessourceTO
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

    fun providePhysicalRessources(bessources: List<BessourceTO>, representationTyp: String): List<BessourceTO> {

        val locationType = when (representationTyp) {
            "HTTP" -> LocationType.MAIN_HTTP
            "FILE" -> LocationType.MAIN_FS
            else -> throw NotFoundException()
        }

        if (bessources.find { it.ressType == RessType.TN.i } == null) {
            val bessourcesWithTN = bessources.first { it.ressType == RessType.PRIMARY.i }.let { b ->
                val tnBessource = BessourceTO(
                    id = -1,
                    name = b.name,
                    mediumId = b.mediumId,
                    ressType = RessType.TN.i,
                    storageId = b.storageId,
                    locationType = when (representationTyp) {
                        "HTTP" -> LocationType.TN_HTTP
                        "FILE" -> LocationType.TN_FS
                        else -> throw NotFoundException()
                    }
                )
                tnBessource
            }
            bessources.plus(bessourcesWithTN)
        }

        bessources.forEach { b ->
            if (b.locationType == null)
                b.locationType = when (representationTyp) {
                    "HTTP" -> {
                        when (b.ressType) {
                            RessType.PRIMARY.i -> LocationType.MAIN_HTTP
                            RessType.TN.i -> LocationType.TN_HTTP
                            else -> throw NotFoundException()
                        }
                    }
                    "FILE" ->   {
                        when (b.ressType) {
                            RessType.TN.i -> LocationType.MAIN_FS
                            RessType.TN.i -> LocationType.TN_FS
                            else -> throw NotFoundException()
                        }
                    }
                    else -> throw NotFoundException()
                }
        }

        return bessources.map { bessource ->
            val url = getUrlFor(bessource)
            if (url != null) {
                bessource.url = url
                bessource
            } else {
                throw NotFoundException()
            }
        }
    }

    fun getUrlFor(bessource: BessourceTO): String? {
        val storage = storageService.findById(bessource.storageId!!);  //NPE possible, but should not happen
        val location = storage.locationInUse(bessource.locationType!!.i)
        if (location == null) return null
        return url(bessource, location)
    }

    fun url(bessource: BessourceTO, location: Location): String {
        return location.uri + "/" +
                (bessource.name.takeIf { location.extension == null }
                    ?: bessource.name!!.replaceFirst("[.][^.]+$".toRegex(), "") + extension)  //todo re extension
    }

    fun url(bessource: Bessource, location: Location): String {
        return location.uri + "/" +
                (bessource.name.takeIf { location.extension == null }
                    ?: bessource.name!!.replaceFirst("[.][^.]+$".toRegex(), "") + extension)
    }


    fun getLocationStartingWith(urls: List<String>): Pair<String, List<Location>> {
        val url = getCommonUrlStart(urls)
        return Pair(url, locationRepository.findLocationsByNameStartingWith(url).filter { it.locationType == 1 })
    }

    fun getCommonUrlStart(urls: List<String>): String {
        val commonStart = getCommonStart(urls)
        val uri = URI(commonStart)
        return uri.scheme + "://" + uri.getHost()
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
