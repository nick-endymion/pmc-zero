package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.to.FileSystemEntryTO
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

    /**
     * Checks that the folder the FS urls of this location are generated into is accessible on the
     * file system. Answers false for HTTP locations, which have no file system path.
     */
    @GetMapping("/{id}/fs-accessible")
    fun isFileSystemAccessible(@PathVariable("id") id: Int): Boolean {
        return locationService.isFileSystemAccessible(id)
    }

    /**
     * Lists the files behind the urls of this location, or of one of its subdirectories.
     *
     * Every entry reports its name relative to the listed directory and its path relative to the
     * root of the location, so the path can be handed back as the subdir of a new listing.
     *
     * @param subdir directory relative to the location, the location itself when omitted
     * @param recursive return the whole tree below that directory instead of only its children
     */
    @GetMapping("/{id}/fs-listing")
    fun listDirectory(
        @PathVariable("id") id: Int,
        @RequestParam(name = "subdir", required = false) subdir: String?,
        @RequestParam(name = "recursive", required = false, defaultValue = "false") recursive: Boolean
    ): List<FileSystemEntryTO> {
        return locationService.listDirectory(id, subdir, recursive)
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
