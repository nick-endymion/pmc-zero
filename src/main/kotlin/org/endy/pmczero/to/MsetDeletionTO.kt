package org.endy.pmczero.to

/**
 * What the deletion of a whole set removed and moved, see
 * `MsetService.deleteIncludingMarkedFiles`.
 *
 * The set, all of its media and all of their bessources are gone. What differs from
 * `DELETE /api/msets/{id}` is the files: the ones behind the media that were marked deleted have been
 * moved below the `DELETED` folder of their location, and the ones behind the media that were not
 * marked are still where they were, since nobody tagged them for it.
 *
 * [media] is one entry per medium that was marked, in id order, so a caller can tell which media a
 * deletion touched and how much each of them moved; [movedFiles] and [skippedFiles] are the same
 * numbers over all of them, which is what a summary of the call needs. A set with no marked media
 * answers an empty [media] rather than nothing, which is the state of a set nobody has tagged
 * anything in and not an error.
 */
data class MsetDeletionTO(
    val msetId: Int,
    val name: String?,

    /** what happened to each medium that was marked deleted, in id order */
    val media: List<MediumDeletionTO>
) {
    /** how many media of the set carried the mark and were deleted along with their files */
    val deletedMedia: Int get() = media.size

    /** the files that now sit below the `DELETED` folder of their location */
    val movedFiles: Int get() = media.sumOf { it.movedFiles }

    /** the files that were left alone: no location to move them in, no file behind the bessource, ... */
    val skippedFiles: Int get() = media.sumOf { it.skippedFiles }
}