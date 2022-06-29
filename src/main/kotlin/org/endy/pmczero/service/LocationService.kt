package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.repository.LocationRepository
import org.springframework.stereotype.Service

@Service
class LocationService(private val locationRepository: LocationRepository) {

    fun url(bessource: Bessource, location: Location): String {

        return location.uri + "/" + bessource.name
//        val folder = mfile.folder ?: throw Exception()
//        return location.uri + "/" + folder.lfolder+ "/"+folder.mpath+"/"+mfile.filename

    }

    fun getLocationStartingWith(url: String): Location {
        val locations = locationRepository.findLocationsByNameStartingWith(url)
        if (locations.size != 1)
            throw Exception()
        return locations[0]
    }

}
