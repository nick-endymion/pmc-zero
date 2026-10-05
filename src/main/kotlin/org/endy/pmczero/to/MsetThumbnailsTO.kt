package org.endy.pmczero.to

/**
 * What a batch of thumbnail generations did for the media of one mset.
 *
 * A batch is not all-or-nothing, so the outcome is split three ways rather than answered as a plain
 * list. [thumbnails] holds every medium a thumbnail could be made for, and each entry carries its own
 * [ThumbnailTO.created] flag, so a caller can tell what was generated from what was already there.
 * [skipped] holds the media that have no primary bessource at all and so have nothing to derive a
 * thumbnail from, which is a normal state for migrated media of type (legacy) folder rather than a
 * failure. [failures] holds the media where generation was attempted and could not be finished.
 *
 * The three are separate lists rather than one list with a status field so a caller that only wants
 * the successes does not have to filter, and so a caller that wants to retry knows exactly which
 * media to retry: everything in [failures] is safe to retry, nothing else is.
 */
data class MsetThumbnailsTO(
    val msetId: Int,

    /** the media of the mset, however they were distributed over the three buckets below */
    val total: Int,
    val created: Int,
    val unchanged: Int,
    val failed: Int,

    val thumbnails: List<ThumbnailTO> = emptyList(),

    /** the ids of the media with no primary bessource, so nothing to make a thumbnail from */
    val skipped: List<Int> = emptyList(),

    val failures: List<ThumbnailFailureTO> = emptyList()
)

/**
 * One medium a batch could not produce a thumbnail for, and why.
 *
 * The reason is the message of the exception the single medium call threw, which is what tells the
 * caller whether the medium is worth retrying: a missing file is a problem on disk that a later run
 * may find fixed, whereas a storage without a TN_FS location will fail the same way forever until
 * somebody configures that storage.
 */
data class ThumbnailFailureTO(
    val mediumId: Int,
    val mediumName: String?,
    val reason: String
)