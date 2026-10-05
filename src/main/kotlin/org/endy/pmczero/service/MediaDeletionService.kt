package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.to.MediumDeletionTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.persistence.EntityManager

/**
 * In front of the file name of a file whose medium was deleted.
 *
 * A constant rather than a parameter because it is what makes a deletion recognisable on disk, and
 * a configurable one would mean files that cannot be told apart from the ones that were never
 * deleted.
 */
private const val PREFIX = "deleted_"

/**
 * Deletes a medium that has been marked deleted, for real, along with its files.
 *
 * Two steps that must not be confused: [MediaService.setDeleted] only marks a medium, this removes
 * it. The mark is the step that is reversible, so this one is not, which is why it insists on the
 * mark being set first.
 *
 * What is removed: the medium row, its bessources, and the files they point at. The files are not
 * erased but renamed with `deleted_` in front, so a deletion that turns out to have been a mistake
 * can still be undone by hand as long as nobody has cleaned the folder up.
 *
 * Only the files that can actually be located are renamed. A bessource points at a storage and a
 * name, and only the MAIN_FS and TN_FS locations of that storage are folders on disk; a bessource on
 * an HTTP location names something that cannot be renamed here, and one whose file is gone has
 * nothing to rename. Neither stops the deletion: the row goes either way, because the point of the
 * call is to remove the record, and a file that cannot be found is not a reason to keep a stale one.
 */
@Service
class MediaDeletionService(
    private val mediaRepository: MediaRepository,
    private val storageService: StorageService,
    private val entityManager: EntityManager
) {

    /**
     * One file that will be renamed, together with the name it will carry afterwards.
     *
     * Both are resolved before anything is touched, see [plan].
     */
    private data class Rename(val from: File, val to: File)

    /**
     * Deletes the medium with [id] and its bessources, then renames the files behind them.
     *
     * Refuses a medium that is not marked deleted, see [Medium.deleted]: the mark is what separates
     * "this is not wanted any more" from "this was never resolved". An unmarked medium is not an
     * error state to be worked around, it is a request that has not been made yet.
     *
     * The rows go before the files, which is the order asked for and also the bounded one: renaming
     * files is not transactional, so a rename that fails afterwards cannot be rolled back. With this
     * order the worst case is a file that kept its old name and a record that is gone, rather than
     * a record that is gone and a file nothing points at any more.
     *
     * The collision check is the exception to that order. It has to run before the deletion, because
     * once the record is gone there is nothing left to answer for a file that was already there, and
     * the caller would hold neither the old nor the new name.
     *
     * @throws NotFoundException when no medium with that id exists
     * @throws NotAccessibleException when the medium is not marked deleted, or when a file to be
     * renamed is already there under its new name
     */
    @Transactional
    fun deleteMarkedMedium(id: Int): MediumDeletionTO {
        val medium = mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()

        if (medium.deleted != true)
            throw NotAccessibleException(
                "medium $id is not marked deleted, so it cannot be deleted. " +
                    "Mark it via PUT /api/media/$id/deleted first."
            )

        val plan = plan(medium)

        // every target is checked before anything is removed: one collision must not leave half a
        // medium deleted
        plan.renames.firstOrNull { it.to.exists() }?.let {
            throw NotAccessibleException(
                "cannot delete medium $id: ${it.to.name} is already there. " +
                    "Move that file aside first, nothing was deleted."
            )
        }

        // the bessources go with the medium, see Medium.bessources: they are meaningless without it
        // and the database would refuse to delete a medium they still point at
        mediaRepository.delete(medium)
        // flushed rather than left to the commit: the delete has to have reached the database before
        // the files move, otherwise a failure here rolls the transaction back and the files are
        // renamed for a record that is still there
        entityManager.flush()

        val renamed = plan.renames.count { move(it, id) }

        return MediumDeletionTO(
            mediumId = id,
            name = medium.name,
            renamedFiles = renamed,
            // everything that was no rename: no folder to rename it in, no file behind the
            // bessource, a name already marked, and the rare case of a rename that failed
            skippedFiles = plan.skipped + (plan.renames.size - renamed)
        )
    }

    /** What [plan] resolved: the files to move, and the count of ones it left out. */
    private data class Plan(val renames: List<Rename>, val skipped: Int)

    /**
     * The files behind [medium] that can be renamed, resolved before anything is deleted.
     *
     * A file that cannot be located is left out rather than reported: there is nothing to rename for
     * it, and the deletion is not blocked by its absence. A file that is already carrying the
     * `deleted_` is left out too, so a second call does not grow the name further.
     */
    private fun plan(medium: Medium): Plan {
        // read eagerly: the bessources are gone once the medium is deleted
        val bessources = medium.bessources.toList()
        val renames = mutableListOf<Rename>()
        // counted rather than dropped silently, so the answer can say that a bessource was left
        // alone instead of leaving the caller to wonder where its file went
        var skipped = 0

        for (bessource in bessources) {
            val rename = renameOf(bessource)
            if (rename == null) skipped++ else renames.add(rename)
        }

        return Plan(renames, skipped)
    }

    /**
     * The rename [bessource] calls for, or null when there is nothing to rename: no folder on disk
     * for it, a name that does not stay inside that folder, no file behind it, or a name that
     * already carries the mark.
     */
    private fun renameOf(bessource: Bessource): Rename? {
        val location = locationOf(bessource) ?: return null
        val relative = bessource.name ?: return null
        val from = fileIn(location, relative) ?: return null
        if (!from.isFile) return null

        val marked = prefixed(relative)
        // already carrying the prefix, so there is nothing to add and a second pass must not grow
        // the name further
        if (marked == relative) return null

        return Rename(from, fileIn(location, marked) ?: return null)
    }

    /**
     * The location of the file [bessource] points at, null when there is none to speak of.
     *
     * The location type follows the ressource type the same way
     * [LocationService.providePhysicalRessources] maps them: a primary file lives in the MAIN_FS
     * location, a thumbnail in the TN_FS one. A bessource of any other type has no folder of its
     * own here, and an HTTP location has no folder at all.
     */
    private fun locationOf(bessource: Bessource): Location? {
        val storage: Storage = bessource.storageOrNull() ?: return null
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
     * The name [relative] carries once it is marked deleted: `deleted_` in front of the file name,
     * keeping the directory and the extension where they are.
     *
     * `tn/2020/shot.jpg` becomes `tn/2020/deleted_shot.jpg`, not `deleted_tn/2020/shot.jpg`: the
     * prefix belongs to the file, and pushing it into the directory would name the directory instead.
     *
     * The extension is kept so the file still opens in whatever handles that extension, and so a
     * half deleted folder stays legible.
     */
    private fun prefixed(relative: String): String {
        val normalised = relative.replace('\\', '/')
        val directory = normalised.substringBeforeLast('/', "")
        val fileName = normalised.substringAfterLast('/')
        // the stem is what the prefix belongs on: a file already carrying it must not grow a second
        // one, so the check is on the stem rather than on the whole name, where `deleted_` would also
        // match a file whose extension happens to start that way
        if (fileName.startsWith(PREFIX)) return normalised

        val extension = fileName.substringAfterLast('.', "")
        val stem = if (extension.isEmpty()) fileName else fileName.dropLast(extension.length + 1)
        val marked = "$PREFIX$stem" + if (extension.isEmpty()) "" else ".$extension"

        return if (directory.isEmpty()) marked else "$directory/$marked"
    }

    /**
     * Moves [rename] into place, answering whether it happened.
     *
     * A failure is reported rather than thrown: the record is gone by now, so there is nothing left
     * to roll back to, and refusing to answer would hide a deletion that did in fact happen. The
     * file stays where it was, under its old name.
     */
    private fun move(rename: Rename, mediumId: Int): Boolean = try {
        // via Files.move rather than File.renameTo, which refuses to replace an existing file on
        // windows; the collision was ruled out above, so nothing is overwritten here either
        Files.move(rename.from.toPath(), rename.to.toPath())
        true
    } catch (e: IOException) {
        println("could not rename ${rename.from.path} to ${rename.to.path} for medium $mediumId: ${e.message}")
        false
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks: a bessource
     * name is free text and a rename must never reach outside the location it belongs to.
     */
    private fun fileIn(location: Location, relative: String?): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = File(root, relative ?: return null).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }
}

