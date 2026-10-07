package org.endy.pmczero.to

/**
 * What a deletion of one medium removed and moved, see
 * `MediaDeletionService.deleteMarkedMedium`.
 *
 * `movedFiles` counts the files that now sit below the `DELETED` folder of their location, keeping the
 * path they were stored under, `skippedFiles` the ones that were left alone: no location to move them
 * in, no file behind the bessource, or a name that was already below that folder.
 */
data class MediumDeletionTO(
    val mediumId: Int,
    val name: String?,
    val movedFiles: Int,
    val skippedFiles: Int
)