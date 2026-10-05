package org.endy.pmczero.to

/**
 * What a deletion of one medium removed and renamed, see
 * `MediaDeletionService.deleteMarkedMedium`.
 *
 * `renamedFiles` counts the files that actually carry the `deleted_` prefix now, `skippedFiles` the
 * ones that were left alone: no location to rename them in, no file behind the bessource, or a name
 * that was already marked.
 */
data class MediumDeletionTO(
    val mediumId: Int,
    val name: String?,
    val renamedFiles: Int,
    val skippedFiles: Int
)