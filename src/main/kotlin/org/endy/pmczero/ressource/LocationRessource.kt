package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toScanTO
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.to.FileSystemEntryTO
import org.endy.pmczero.to.LocationTO
import org.endy.pmczero.to.MsetScanTO
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
     * A listed file is flagged as existsAlready when a medium with a primary bessource pointing at
     * that file is stored already, so a scan can skip what is known. Directories are always false.
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
     * Files that are stored already produce no medium, the draft holds only what a scan would still
     * create. Ask GET on fs-listing for the existsAlready flag of each file.
     *
     * Nothing to scan is answered rather than refused: a directory that holds nothing new comes back
     * as an empty mset, and [MsetScanTO.knownFiles] says how many files it holds so a caller can tell
     * that from a scan that never ran. The same answer shape as the other two scan endpoints.
     *
     * Answers 404 when the location does not exist or the subpath does not, and 409 when the location
     * cannot be listed or has no storage to build bessources against.
     *
     * @param subpath directory relative to the location, the location itself when omitted
     */
    @GetMapping("/{id}/fs-mset")
    fun draftMset(
        @PathVariable("id") id: Int,
        @RequestParam(name = "subpath", required = false) subpath: String?
    ): MsetScanTO {
        val scan = locationService.draftMset(id, subpath)

        return scan.mset.toScanTO(scan.addedFiles, scan.knownFiles)
    }

    /**
     * Scans a directory of this location and saves the mset it produces, answering what was saved.
     * The same scan as GET on this path, persisted, in the same [MsetScanTO] shape.
     *
     * The mset is saved as a new row every call, so scanning the same directory twice leaves two
     * msets behind. Its media are not created twice though: a file that is stored already, so one
     * whose listing entry reports existsAlready, produces no medium. Use POST on
     * [expandMset] to add what a directory has gained since, rather than leaving a second set behind.
     *
     * A directory that holds nothing new is still saved, as an empty set. That is what a scan of a
     * directory whose files are all stored already produces, and the counts in the answer are what
     * says so; expand the set it belongs to instead if an empty set is not wanted.
     *
     * @param subpath directory relative to the location, the location itself when omitted
     */
    @PostMapping("/{id}/fs-mset")
    fun createMsetFromDirectory(
        @PathVariable("id") id: Int,
        @RequestParam(name = "subpath", required = false) subpath: String?
    ): MsetScanTO {
        val scan = locationService.draftMset(id, subpath)
        val saved = msetService.save(scan.mset)

        // re-read so the response carries the generated ids and timestamps of the saved rows
        return msetService.findById(saved.id!!, withMedia = true)
            .toScanTO(scan.addedFiles, scan.knownFiles)
    }

    /**
     * Adds the files of a directory of this location that have no medium yet to an mset that exists
     * already, answering what was added and the set as it was saved.
     *
     * The same scan as the other two on this path, the same rules about which files count, and the
     * same [MsetScanTO] answer. The only difference is where the media go: into the set named by
     * [msetId] rather than into a new one. So a directory that has grown since its set was created
     * is picked up without producing a second set for the same directory, which is what scanning it
     * again through that endpoint would do.
     *
     * The media already in the set are left alone, and so is the location the set records: that is
     * where it came from, not where it is being extended to.
     *
     * Nothing new is answered rather than refused, since that is the normal state of a directory that
     * has not changed. Read [MsetScanTO.addedFiles] to tell it from a scan that did something.
     *
     * Answers 404 when the mset does not exist and when the subpath does not exist or points outside
     * of the location, and 409 when the location cannot be listed or has no storage to build bessources
     * against.
     *
     * @param msetId the set to add the media to
     * @param subpath directory relative to the location, the directory the set itself records when
     * omitted
     */
    @PostMapping("/{id}/fs-mset/expand")
    fun expandMset(
        @PathVariable("id") id: Int,
        @RequestParam("msetId") msetId: Int,
        @RequestParam(name = "subpath", required = false) subpath: String?
    ): MsetScanTO {
        return msetService.expandMset(msetId, id, subpath)
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
