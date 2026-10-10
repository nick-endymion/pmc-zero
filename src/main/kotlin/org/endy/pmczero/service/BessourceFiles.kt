package org.endy.pmczero.service

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.springframework.stereotype.Service
import java.io.File

/**
 * The file on disk that a [Bessource] points at.
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
     * The location of the file [bessource] points at, null when there is none to speak of.
     *
     * The location type follows the ressource type the same way
     * [LocationService.providePhysicalRessources] maps them: a primary file lives in the MAIN_FS
     * location, a thumbnail in the TN_FS one. A bessource of any other type has no folder of its own
     * here, and an HTTP location has no folder at all.
     */
    private fun locationOf(bessource: Bessource): Location? {
        val storage = bessource.storageOrNull() ?: return null
        val wanted = when (bessource.ressType) {
            RessType.PRIMARY.i -> LocationType.MAIN_FS
            RessType.TN.i -> LocationType.TN_FS
            else -> return null
        }

        // withLocations: the locations are lazy, so reading one without them would fail outside a
        // session and load nothing useful with them
        return storageService.findById(storage.id ?: return null, withLocations = true)
            .locationInUse(wanted.i)
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks: a bessource name
     * is free text and nothing done with one may reach outside the location it belongs to.
     */
    private fun fileIn(location: Location, relative: String?): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = File(root, relative ?: return null).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }
}
