package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.to.FileSystemEntryTO
import org.endy.pmczero.to.LocationTO
import org.endy.pmczero.to.MsetTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationRessource(
    val locationService: LocationService,
    val msetService: MsetService
) {

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

    /**
     * Answers the mset that scanning a directory of this location would produce, without saving
     * anything: one medium per file below that directory, each with the bessource pointing at it.
     * The caller can review and change the draft before saving it via POST to this same path.
     *
     * @param subpath directory relative to the location, the location itself when omitted
     */
    @GetMapping("/{id}/fs-mset")
    fun draftMset(
        @PathVariable("id") id: Int,
        @RequestParam(name = "subpath", required = false) subpath: String?
    ): MsetTO {
        return locationService.draftMset(id, subpath).toTOwithMedia(true)
    }

    /**
     * Scans a directory of this location and saves the mset it produces, answering the saved mset
     * with its media. The same as the draft of GET on this path, but persisted.
     *
     * The mset is saved as a new row every call, so scanning the same directory twice leaves two
     * msets behind.
     *
     * @param subpath directory relative to the location, the location itself when omitted
     */
    @PostMapping("/{id}/fs-mset")
    fun createMsetFromDirectory(
        @PathVariable("id") id: Int,
        @RequestParam(name = "subpath", required = false) subpath: String?
    ): MsetTO {
        val saved = msetService.save(locationService.draftMset(id, subpath))
        // re-read so the response carries the generated ids and timestamps of the saved rows
        return msetService.findById(saved.id!!, withMedia = true).toTOwithMedia(true)
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
