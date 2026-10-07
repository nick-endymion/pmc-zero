package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.to.BessourceTO
import org.endy.pmczero.to.FileSystemEntryTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.File
import java.net.URI
import java.time.Instant

@Service
class LocationService(
    private val locationRepository: LocationRepository,
    private val storageService: StorageService,
    private val bessourceRepository: BessourceRepository
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

    /**
     * Derives the physical location of every given [bessources], assigns the matching
     * [LocationType] and fills in the url.
     *
     * @param generateThumbnail when true (default) and no bessource of [RessType.TN] is given, a
     * thumbnailed bessource is derived from the [RessType.PRIMARY] one and appended. Callers that
     * resolve a single bessource pass false, otherwise a missing TN location would fail the whole call.
     */
    fun providePhysicalRessources(
        bessources: List<BessourceTO>,
        representationTyp: String,
        generateThumbnail: Boolean = true
    ): List<BessourceTO> {

        val (mainLocationType, tnLocationType) = when (representationTyp) {
            "HTTP" -> LocationType.MAIN_HTTP to LocationType.TN_HTTP
            "FILE" -> LocationType.MAIN_FS to LocationType.TN_FS
            else -> throw NotFoundException()
        }

        var allBessources = bessources

        if (generateThumbnail && allBessources.find { it.ressType == RessType.TN.i } == null) {
            val primary = allBessources.first { it.ressType == RessType.PRIMARY.i }
            allBessources = allBessources.plus(
                BessourceTO(
                    id = -1,
                    name = primary.name,
                    mediumId = primary.mediumId,
                    ressType = RessType.TN.i,
                    storageId = primary.storageId,
                    locationType = tnLocationType
                )
            )
        }

        allBessources.forEach { b ->
            if (b.locationType == null)
                b.locationType = when (b.ressType) {
                    RessType.PRIMARY.i -> mainLocationType
                    RessType.TN.i -> tnLocationType
                    else -> throw NotFoundException()
                }
        }

        return allBessources.map { bessource ->
            bessource.url = getUrlFor(bessource)
            bessource
        }
    }

    fun getUrlFor(bessource: BessourceTO): String? {
        val storageId = bessource.storageId ?: return null
        val locationType = bessource.locationType ?: return null
        val storage = storageService.findById(storageId)
        val location = storage.locationInUse(locationType.i)
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

    /**
     * Checks that the folder [providePhysicalRessources] generates its file urls into is
     * accessible on the file system, so an FS location can be validated before it is used.
     *
     * A location counts as accessible when its [Location.uri] points to an existing path that can
     * both be read and written. HTTP locations ([LocationType.MAIN_HTTP], [LocationType.TN_HTTP])
     * have no file system path, so false is returned for them, as it is for a location without uri.
     *
     * @throws NotFoundException when no location with that id exists
     */
    fun isFileSystemAccessible(locationId: Int): Boolean {
        return accessibleFolderOrNull(findById(locationId)) != null
    }

    /**
     * Lists the directory of an FS location, so the files behind the urls of
     * [providePhysicalRessources] can be browsed.
     *
     * @param subdir directory to list, relative to the location. Null or blank lists the location
     * itself. It may not point outside of the location, so browsing cannot escape the location root.
     * @param recursive when true the whole tree below the listed directory is returned, otherwise
     * only its direct children
     * @return the entries of that directory, directories first, each one ordered by name. The
     * listing starts with a '.' entry for the listed directory itself, followed by a '..' entry for
     * its parent when that parent lies within the location, so the caller can both stay put and walk
     * back up without ever leaving the location. The '..' entry is left out on the location itself,
     * which has no parent to walk up to. Every entry carries [FileSystemEntryTO.name], relative to
     * the listed directory, and [FileSystemEntryTO.path], relative to the location root.
     *
     * Every listed file also reports in [FileSystemEntryTO.existsAlready] whether a medium with a
     * primary bessource pointing at it is stored already, so a caller can leave known files out of
     * a scan. Without a storage to compare against nothing can be known, so the flag stays false.
     *
     * @throws NotFoundException when no location with that id exists, or when [subdir] does not
     * exist or points outside of the location
     * @throws NotAccessibleException when the location is not an FS location or its path is not
     * accessible on the file system
     */
    fun listDirectory(
        locationId: Int,
        subdir: String? = null,
        recursive: Boolean = false
    ): List<FileSystemEntryTO> {
        val location = findById(locationId)
        // canonical, so that the entries and the root can be related to each other by path
        val root = (accessibleFolderOrNull(location)
            ?: throw NotAccessibleException("location $locationId is not an accessible file system location")).canonicalFile
        val folder = subfolderOrNull(root, subdir) ?: throw NotFoundException()

        val entries =
            if (recursive) folder.walkTopDown().drop(1)
            else folder.listFiles()?.asSequence() ?: emptySequence()

        // collected first, so the whole listing is checked against the database in one query
        val unsorted = entries.map { entryTO(root, folder, it) }.toList()
        val marked = unsorted.markExisting(location)

        val listed = marked
            .sortedWith(compareByDescending<FileSystemEntryTO> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()

        return listOf(selfEntry(root, folder)) + listOfNotNull(parentEntryOrNull(root, folder)) + listed
    }

    /**
     * the [entries] with [FileSystemEntryTO.existsAlready] set on those a scan would not have to
     * create, i.e. on those whose path is already the name of a primary bessource of a medium in
     * the storage of [location]
     *
     * Directories are left untouched, a directory is no medium. Without a storage there is nothing
     * to compare against and no bessource could point at the location anyway, so every file stays
     * false instead of asking the database about names of a storage that does not exist.
     */
    private fun List<FileSystemEntryTO>.markExisting(location: Location): List<FileSystemEntryTO> {
        val storageId = location.storageOrNull()?.id ?: return this
        val files = filter { !it.isDirectory }
        if (files.isEmpty()) return this
        val known = bessourceRepository
            .findNamesOfExistingMedia(storageId, RessType.PRIMARY.i, files.map { it.path })
            .toSet()
        if (known.isEmpty()) return this
        return map { if (it.path in known) it.copy(existsAlready = true) else it }
    }

    /**
     * Builds the [Mset] that scanning [subdir] of an FS location would produce, without persisting
     * anything: one [Medium] per file below that directory, each carrying the [Bessource] that
     * points at the file. It is the draft counterpart of [listDirectory], so a caller can review
     * what a scan would create, and change it, before saving it via [MsetService.save].
     *
     * The mset is named after [subdir], or after the location when that is blank, and records where it
     * came from in [Mset.locationId] and [Mset.subpath], so the draft already says which location and
     * which directory below it the set is about. A [Medium] is
     * named after the file name alone, so two files of the same name in different subdirectories
     * end up as two equally named media, while its [Bessource] is named after the file relative to
     * the location, which is the form [url] needs to build a working url. The type of a medium is
     * derived from the file extension, see [Mtype.of].
     *
     * Subdirectories are traversed but produce no medium of their own, a directory is not a medium
     * and has no ressource pointing at it. The '.' and '..' entries of the listing are skipped for
     * the same reason.
     *
     * Files that are stored already, so those whose listing entry reports
     * [FileSystemEntryTO.existsAlready], produce no medium either. The draft therefore holds only
     * what a scan would still have to create, and scanning the same directory twice does not create
     * a second medium for a file the first scan already stored.
     *
     * Nothing to scan is reported as an error instead of as an empty mset, so a caller cannot
     * mistake a scan that found nothing for one that succeeded. That also covers a directory that
     * holds no files at all: it has nothing to add either. Ask GET on [listDirectory] for the
     * [FileSystemEntryTO.existsAlready] flag of each file to see what is stored already.
     *
     * @throws NotFoundException when no location with that id exists, or when [subdir] does not
     * exist or points outside of the location
     * @throws NotAccessibleException when the location is not an FS location, its path is not
     * accessible on the file system, or there is no file left to scan because everything is stored
     * already
     */
    fun draftMset(locationId: Int, subdir: String? = null): Mset {
        val location = findById(locationId)
        val entries = unsavedEntries(locationId, subdir)
        // thrown before any mset is built, so a scan that finds nothing leaves no set behind
        if (entries.isEmpty()) throw NotAccessibleException(
            "nothing to scan in location $locationId" +
                (subdir?.takeIf { it.isNotBlank() }?.let { " below $it" } ?: "") +
                ": every file is stored already"
        )
        // a bessource always references a storage, without one it cannot even be instantiated
        val storage = storageOf(location)

        val mset = Mset().apply {
            name = subdir?.takeIf { it.isNotBlank() } ?: location.name
            this.locationId = location.id
            this.subpath = subpathOf(subdir)
        }

        mset.media = entries.map { entry -> mediumOf(entry, mset, storage) }.toMutableList()

        return mset
    }

    /** What [expandMset] added to a set: the new media, and how many files it left out. */
    data class Expansion(val added: List<Medium>, val knownFiles: Int)

    /**
     * Adds the files below [subdir] of an FS location to [mset] that have no medium yet, the same
     * ones [draftMset] would have built a new set of, and answers what it added.
     *
     * The counterpart of [draftMset] for a set that exists already. Everything that decides which
     * files count is the same and deliberately so: a file that is stored already produces no medium
     * either way, so expanding a set twice does not create a second medium for a file the first run
     * already stored, and expanding a set that was built from the same directory adds nothing at all.
     *
     * Nothing new is not an error. A scan that finds nothing has nothing to add, which is what a
     * repeated call over an unchanged directory should look like, so this answers an empty [Expansion]
     * rather than throwing the way [draftMset] does: there a new set is what was asked for and an
     * empty one would be a lie, here nothing was.
     *
     * The media are added to [mset] in memory, not persisted: saving is the caller's, which is what
     * [draftMset] does as well, so the two can be reviewed before they are written.
     *
     * @throws NotFoundException when no location with that id exists, or when [subdir] does not exist
     * or points outside of the location
     * @throws NotAccessibleException when the location is not an FS location or its path is not
     * accessible, or when it has no storage and there is something to add
     */
    fun expandMset(mset: Mset, locationId: Int, subdir: String? = null): Expansion {
        val location = findById(locationId)
        val entries = listDirectory(locationId, subdir, recursive = true)
        // the unsaved files, and the stored ones counted alongside them, since a caller expanding a
        // directory wants to hear that it was already up to date rather than only that nothing was
        // added
        val unsaved = entries.filter { !it.isDirectory && !it.existsAlready }
        val known = entries.count { !it.isDirectory && it.existsAlready }

        if (unsaved.isEmpty()) return Expansion(emptyList(), known)

        val storage = storageOf(location)
        val added = unsaved.map { entry -> mediumOf(entry, mset, storage) }
        mset.media.addAll(added)

        return Expansion(added, known)
    }

    /**
     * The files below [subdir] that a scan would still have to create a medium for, i.e. the
     * non-directories of a recursive listing that are not stored already.
     */
    private fun unsavedEntries(locationId: Int, subdir: String?): List<FileSystemEntryTO> =
        // listDirectory answers the '.', '..' and escaping subdir problems the same way as the
        // listing itself, so no draft can be built for a directory that cannot be listed
        listDirectory(locationId, subdir, recursive = true)
            .filter { !it.isDirectory && !it.existsAlready }

    /** the [Storage] the bessources of a medium built for [location] point at */
    private fun storageOf(location: Location): Storage =
        // a bessource always references a storage, without one it cannot even be instantiated
        location.storageOrNull()
            ?: throw NotAccessibleException(
                "location ${location.id} has no storage to build bessources against"
            )

    /**
     * [subdir] in the form [Mset.subpath] documents: `/` separated, no leading or trailing slash, so
     * it can be handed straight back as the `subpath` of another call. Empty is null, since the
     * location root has no subpath below it.
     */
    private fun subpathOf(subdir: String?): String? =
        subdir?.normaliseSubpath()?.takeIf { it.isNotEmpty() }

    /**
     * the [Medium] for the listed file [entry], named after the file itself, with the [Bessource]
     * that points at the file, named relative to the location so [url] can use it
     */
    private fun mediumOf(entry: FileSystemEntryTO, mset: Mset, storage: Storage): Medium {
        // a recursive listing reports the path of a file below the scanned directory, a medium is
        // named after the file itself
        val fileName = entry.name.substringAfterLast('/')
        val medium = Medium().apply {
            name = fileName
            mtype = Mtype.of(fileName).i
            this.mset = mset
        }
        medium.bessources = mutableListOf(
            Bessource().apply {
                name = entry.path
                ressType = RessType.PRIMARY.i
                this.storage = storage
            }
        )
        // the bessource points back at its medium, the way a saved one would
        medium.bessources.forEach { it.medium = medium }
        return medium
    }

    /**
     * the '.' entry for the listed [folder] itself. Its path is the one of [folder] below [root], so
     * the caller always learns where in the location it currently is.
     */
    private fun selfEntry(root: File, folder: File): FileSystemEntryTO =
        entryTO(root, folder, folder).copy(name = ".")

    /**
     * the '..' entry for the parent of [folder] within [root], null when [folder] is the location
     * itself. Walking up therefore never leaves the location. Only the name is faked, the path is
     * the real one of the parent, so it is empty when the parent is the location root.
     */
    private fun parentEntryOrNull(root: File, folder: File): FileSystemEntryTO? {
        if (folder.canonicalFile == root.canonicalFile) return null
        val parent = folder.parentFile ?: return null
        return entryTO(root, folder, parent).copy(name = "..")
    }

    /** the listing entry of [entry], located in the listed [folder] of the location root [root] */
    private fun entryTO(root: File, folder: File, entry: File): FileSystemEntryTO =
        FileSystemEntryTO(
            name = entry.pathIn(folder),
            path = entry.pathIn(root),
            isDirectory = entry.isDirectory,
            size = entry.length(),
            lastModified = Instant.ofEpochMilli(entry.lastModified())
        )

    /** the accessible folder of an FS location, null for an HTTP location or an unusable path */
    private fun accessibleFolderOrNull(location: Location): File? {
        if (location.locationType != LocationType.MAIN_FS.i && location.locationType != LocationType.TN_FS.i)
            return null
        val folder = location.uri?.let { File(it) } ?: return null
        return if (folder.exists() && folder.canRead() && folder.canWrite()) folder else null
    }

    /** resolves [subdir] inside [root], null when it is missing or escapes [root] */
    private fun subfolderOrNull(root: File, subdir: String?): File? {
        if (subdir.isNullOrBlank()) return root
        val folder = File(root, subdir).canonicalFile
        val rootPath = root.canonicalFile.path
        // canonicalFile resolves '..' and symlinks, so the comparison rules out escaping the location
        if (folder.path != rootPath && !folder.path.startsWith(rootPath + File.separator))
            return null
        return if (folder.isDirectory) folder else null
    }

    /**
     * [subdir] normalised to `/` separators without a leading or trailing slash, the very
     * normalisation [org.endy.pmczero.model.scraper.ScanPath.folderOf] applies to the folder a scraper
     * writes into. A `\` separator becomes `/`, so a caller on windows does not produce a path that
     * says nothing on a posix system.
     */
    private fun String.normaliseSubpath(): String =
        replace('\\', '/').trim('/').let { if (it.isBlank()) "" else it }

    /** path of this file relative to [folder], always with '/' as separator */
    private fun File.pathIn(folder: File): String =
        toRelativeString(folder).replace(File.separatorChar, '/')


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
