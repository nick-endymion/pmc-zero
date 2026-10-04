package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.to.LocationTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationRessource(val locationService: LocationService) {

    @GetMapping("/")
    fun getFolders(): Iterable<LocationTO> {
        return locationService.findAll().map { it.toTO() }
    }

    @GetMapping("/{id}")
    fun getLocation(@PathVariable("id") id: Int): LocationTO {
        return locationService.findById(id).toTO()
    }

    @PostMapping("/default")
    fun blabla(@RequestBody location: Location): Location {
        return locationService.createDefaultLocationWithStorage(location.uri!!)
    }

    @PostMapping("/")
    fun createMset(@RequestBody location: LocationTO): LocationTO {
        val saved = locationService.save(location.toEntity())
        // re-read so the response reflects the persisted row (generated timestamps, loaded storage)
        return locationService.findById(saved.id!!).toTO()
    }

    @PutMapping("/{id}")
    fun updateLocation(@PathVariable("id") id: Int, @RequestBody locationTO: LocationTO): LocationTO {
        val entity = locationTO.toEntity()
        entity.id = id
        locationService.save(entity)
        // re-read: createdAt is updatable=false, so the merged instance does not carry it back
        return locationService.findById(id).toTO()
    }

}
