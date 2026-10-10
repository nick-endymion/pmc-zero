package org.endy.pmczero.service

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

/**
 * The file on disk that a [Bessource] points at, or that a name inside a storage stands for.
 *
 * A bessource names a storage and a path, and neither is a file on its own: the path is relative to the
 * folder of a location, and which location that is follows from the ressource type. So every caller
 * that wants to touch the file of a bessource asks here rather than each deciding, since the two
 * decisions that have to agree are the location a ressource type lives in and whether a name stays
 * inside it.
 */
@Service
class BessourceFiles(private val storageService: StorageService) {

    /**
     * The file [bessource] points at, or null when there is no such file on disk.
     *
     * Null for the four cases that are all one answer: no folder on disk for this ressource type, no
     * name, a name that does not stay inside that folder, or a file that has been deleted since. A
     * caller that has to tell them apart says which one it expected, since a bessource whose file is
     * gone and a bessource with no folder at all are the same kind of answer but not the same problem.
     */
    fun fileOf(bessource: Bessource): File? {
        val location = locationOf(bessource) ?: return null
        return fileIn(location, bessource.name)?.takeIf { it.isFile }
    }

    /**
     * [relative] inside the same folder as the file [bessource] points at, or null when it would leave
     * it.
     *
     * For a caller that writes a second file of the same bessource beside the first, e.g. a deletion
     * that parks a file under a folder of its own. Asked of this and not of [fileOf], since a path
     * that nothing is on yet has no file to be found at.
     */
    fun destinationOf(bessource: Bessource, relative: String): File? {
        val location = locationOf(bessource) ?: return null
        return fileIn(location, relative)
    }

    /**
     * The file [relative] names inside the MAIN_FS folder of the storage [storageId], or null when
     * there is no such file on disk.
     *
     * The file [fileOf] answers, asked without a bessource: for a caller that has a storage and a name
     * and no row to hand, e.g. one serving the `a.bessources.name` of an url straight off disk. Same
     * folder and the same refusal to leave it, since a name is free text whichever end of the
     * application it arrives at.
     *
     * [relative] may name directories of its own, since a bessource name is a path below the location
     * rather than a file name alone.
     *
     * MAIN_FS only, since a name says nothing about which ressource type it belongs to and answering
     * for a thumbnail from the same call would need a second folder asked for by the same name.
     * [fileOf] knows the type from the row and answers either.
     *
     * @throws org.endy.pmczero.exception.NotFoundException when no storage has that id
     */
    fun mainFileOf(storageId: Int, relative: String?): File? {
        val storage = storageService.findById(storageId, withLocations = true)
        val location = locationOf(storage, LocationType.MAIN_FS.i) ?: return null

        return fileIn(location, relative)?.takeIf { it.isFile }
    }

    /**
     * The location of the file [bessource] points at, null when there is none to speak of.
     *
     * The location type follows the ressource type the same way
     * [LocationService.providePhysicalRessources] maps them: a primary file lives in the MAIN_FS
     * location, a thumbnail in the TN_FS one. A bessource of any other type has no folder of its own
     * here, and an HTTP location has no folder at all.
     */
    private fun locationOf(bessource: Bessource): Location? {
        val wanted = when (bessource.ressType) {
            RessType.PRIMARY.i -> LocationType.MAIN_FS.i
            RessType.TN.i -> LocationType.TN_FS.i
            else -> return null
        }

        return locationOf(bessource.storageOrNull(), wanted)
    }

    /**
     * The in use location of the FS type [locationType] of [storage], null when it has none.
     *
     * withLocations: the locations are lazy, so reading one without them would fail outside a session
     * and load nothing useful with them.
     */
    private fun locationOf(storage: Storage?, locationType: Int): Location? {
        val storageId = storage?.id ?: return null
        return storageService.findById(storageId, withLocations = true).locationInUse(locationType)
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks: a bessource name
     * is free text and nothing done with one may reach outside the location it belongs to. A name that
     * cannot be canonicalised at all answers null rather than throwing, since a name arrives from a
     * url here and there are names the file system of the machine refuses to even name.
     */
    private fun fileIn(location: Location, relative: String?): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = canonicalOrNull(File(root, relative ?: return null)) ?: return null

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    /** [file] canonicalised, null when the file system will not name it at all */
    private fun canonicalOrNull(file: File): File? =
        try {
            file.canonicalFile
        } catch (e: IOException) {
            null
        }
}