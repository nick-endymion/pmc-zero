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
 * The folder below the uri of a location that the files of a deleted medium are moved into.
 *
 * A constant rather than a parameter because it is what makes a deletion recognisable on disk, and a
 * configurable one would mean files that cannot be told apart from the ones that were never deleted.
 *
 * It sits below the location rather than beside it, so a deleted file keeps its full path below the
 * location: the name of its bessource is what it was stored under, and that is what is reproduced
 * under this folder. A location holding `imagegap4/abc/984580928.jpg` ends up with
 * `DELETED/imagegap4/abc/984580928.jpg`, so two media deleted out of two different folders do not
 * collide and neither does a folder that held an `a.jpg` in more than one place.
 */
private const val DELETED_FOLDER = "DELETED"

/**
 * Deletes a medium that has been marked deleted, for real, along with its files.
 *
 * Two steps that must not be confused: [MediaService.setDeleted] only marks a medium, this removes
 * it. The mark is the step that is reversible, so this one is not, which is why it insists on the
 * mark being set first.
 *
 * What is removed: the medium row, its bessources, and the files they point at. The files are not
 * erased but moved below a [DELETED_FOLDER] under the uri of their location, keeping the path they
 * were stored under, so a deletion that turns out to have been a mistake can still be undone by hand
 * as long as nobody has cleaned the folder up.
 *
 * Only the files that can actually be located are moved. A bessource points at a storage and a
 * name, and only the MAIN_FS and TN_FS locations of that storage are folders on disk; a bessource on
 * an HTTP location names something that cannot be moved here, and one whose file is gone has
 * nothing to move. Neither stops the deletion: the row goes either way, because the point of the
 * call is to remove the record, and a file that cannot be found is not a reason to keep a stale one.
 */
@Service
class MediaDeletionService(
    private val mediaRepository: MediaRepository,
    private val storageService: StorageService,
    private val entityManager: EntityManager
) {

    /**
     * One file that will be moved, together with the name it will carry afterwards.
     *
     * Both are resolved before anything is touched, see [plan]. [label] is the destination as a path
     * relative to the location, which is what an error message should name: an absolute path says
     * nothing to a caller that does not know where the storage sits.
     */
    private data class Move(val from: File, val to: File, val label: String)

    /**
     * Deletes the medium with [id] and its bessources, then moves the files behind them below the
     * [DELETED_FOLDER] of their location.
     *
     * Refuses a medium that is not marked deleted, see [Medium.deleted]: the mark is what separates
     * "this is not wanted any more" from "this was never resolved". An unmarked medium is not an
     * error state to be worked around, it is a request that has not been made yet.
     *
     * The rows go before the files, which is the order asked for and also the bounded one: moving
     * files is not transactional, so a move that fails afterwards cannot be rolled back. With this
     * order the worst case is a file that kept its place and a record that is gone, rather than a
     * record that is gone and a file nothing points at any more.
     *
     * The collision check is the exception to that order. It has to run before the deletion, because
     * once the record is gone there is nothing left to answer for a file that was already there, and
     * the caller would hold neither the old nor the new place.
     *
     * @throws NotFoundException when no medium with that id exists
     * @throws NotAccessibleException when the medium is not marked deleted, or when a file to be
     * moved is already there under its new name
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
        plan.moves.firstOrNull { it.to.exists() }?.let {
            throw NotAccessibleException(
                "cannot delete medium $id: ${it.label} is already there. " +
                    "Move that file aside first, nothing was deleted."
            )
        }

        // the bessources go with the medium, see Medium.bessources: they are meaningless without it
        // and the database would refuse to delete a medium they still point at
        mediaRepository.delete(medium)
        // flushed rather than left to the commit: the delete has to have reached the database before
        // the files move, otherwise a failure here rolls the transaction back and the files are
        // moved for a record that is still there
        entityManager.flush()

        val moved = plan.moves.count { move(it, id) }

        return MediumDeletionTO(
            mediumId = id,
            name = medium.name,
            movedFiles = moved,
            // everything that was no move: no folder to move it in, no file behind the bessource, a
            // name that is already below the DELETED folder, and the rare case of a move that failed
            skippedFiles = plan.skipped + (plan.moves.size - moved)
        )
    }

    /** What [plan] resolved: the files to move, and the count of ones it left out. */
    private data class Plan(val moves: List<Move>, val skipped: Int)

    /**
     * The files behind [medium] that can be moved, resolved before anything is deleted.
     *
     * A file that cannot be located is left out rather than reported: there is nothing to move for
     * it, and the deletion is not blocked by its absence. A file that already sits below the
     * [DELETED_FOLDER] is left out too, so a second pass does not pile the folder up on itself.
     */
    private fun plan(medium: Medium): Plan {
        // read eagerly: the bessources are gone once the medium is deleted
        val bessources = medium.bessources.toList()
        val moves = mutableListOf<Move>()
        // counted rather than dropped silently, so the answer can say that a bessource was left
        // alone instead of leaving the caller to wonder where its file went
        var skipped = 0

        for (bessource in bessources) {
            val move = moveOf(bessource)
            if (move == null) skipped++ else moves.add(move)
        }

        return Plan(moves, skipped)
    }

    /**
     * The move [bessource] calls for, or null when there is nothing to move: no folder on disk for
     * it, a name that does not stay inside that folder, no file behind it, or a name that is already
     * below the [DELETED_FOLDER].
     */
    private fun moveOf(bessource: Bessource): Move? {
        val location = locationOf(bessource) ?: return null
        val relative = bessource.name ?: return null
        val from = fileIn(location, relative) ?: return null
        if (!from.isFile) return null

        val parked = parked(relative) ?: return null

        return Move(from, fileIn(location, parked) ?: return null, parked)
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
     * The name [relative] carries once it is moved below the [DELETED_FOLDER], or null when there is
     * nothing to move it to: the name is blank, or it already sits below that folder.
     *
     * The whole name is kept below the folder, directories included, so the destination reproduces the
     * path the file was stored under rather than flattening it. `tn/2020/shot.jpg` becomes
     * `DELETED/tn/2020/shot.jpg`, which is what tells two files of the same name in different
     * folders apart after they are gone from where they were.
     *
     * Separators are normalised to `/`, the same way a bessource name is everywhere else in this
     * application, so a name that was written on windows does not become one directory named after a
     * backslash on a posix system.
     *
     * A name that already starts with the folder is refused rather than moved, which is what keeps a
     * second pass over the same medium from producing `DELETED/DELETED/...`. It is the counterpart of
     * what the file name check used to do when a deletion was expressed as a prefix on the name.
     */
    private fun parked(relative: String): String? {
        val normalised = relative.replace('\\', '/').trim('/')
        if (normalised.isBlank()) return null
        if (normalised == DELETED_FOLDER || normalised.startsWith("$DELETED_FOLDER/")) return null

        return "$DELETED_FOLDER/$normalised"
    }

    /**
     * Moves [move] into place, answering whether it happened.
     *
     * A failure is reported rather than thrown: the record is gone by now, so there is nothing left
     * to roll back to, and refusing to answer would hide a deletion that did in fact happen. The
     * file stays where it was, under its old name.
     */
    private fun move(move: Move, mediumId: Int): Boolean = try {
        // the destination is a folder that does not exist until the first file goes into it, and one
        // that nests the whole path of the file, so every folder on the way has to be made here
        move.to.parentFile?.mkdirs()
        // via Files.move rather than File.renameTo, which refuses to replace an existing file on
        // windows; the collision was ruled out above, so nothing is overwritten here either
        Files.move(move.from.toPath(), move.to.toPath())
        true
    } catch (e: IOException) {
        println("could not move ${move.from.path} to ${move.to.path} for medium $mediumId: ${e.message}")
        false
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks: a bessource
     * name is free text and a move must never reach outside the location it belongs to.
     */
    private fun fileIn(location: Location, relative: String?): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = File(root, relative ?: return null).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }
}