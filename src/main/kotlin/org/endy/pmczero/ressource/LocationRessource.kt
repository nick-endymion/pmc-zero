package org.endy.pmczero.ressource

import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.LocationService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationRessource(val locationService: LocationService) {

    @GetMapping("/")
    fun getFolders(): Iterable<Location> {
        return locationService.findAll()
    }

    @PostMapping("/")
    fun createMset(@RequestBody location: Location): Location {
        return locationService.createDefaultLocationWithStorage(location.uri!!)
    }

}
