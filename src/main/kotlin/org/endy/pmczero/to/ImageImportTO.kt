package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Medium

/**
 * What an import of the images of one page did.
 *
 * Not all-or-nothing, and deliberately so: a gallery of two hundred images routinely holds a handful
 * that 404, that answer an image the browser cannot store, or that need a session the browser does not
 * have. Failing the whole call on the first of those would make it useless for exactly the galleries
 * it is meant for, so each image is attempted on its own and its outcome reported separately.
 *
 * [imported] holds what really landed on disk, [skipped] what was there already and therefore left
 * alone, [failures] what was attempted and could not be finished. Everything in [failures] is safe to
 * retry; nothing else is, so the split is what tells a caller whether calling again is worth it.
 */
data class ImageImportTO(
    val locationId: Int,

    /** the page the images were taken from */
    val url: String,

    /** the storage the files were written to, or null when nothing was written */
    val storageId: Int? = null,

    /** the id of the mset that holds the imported media, or null when [persist] was false */
    val msetId: Int? = null,

    /** how many image urls the page held */
    val found: Int = 0,

    /** how many files were written and turned into media */
    val imported: Int = 0,

    /** how many were there already, so nothing was written for them */
    val skipped: Int = 0,

    /** how many could not be fetched or written */
    val failed: Int = 0,

    /** the imported media, so a caller sees what was created without a second call */
    val media: List<MediumTO> = emptyList(),

    /** the images that failed, with the reason each one did */
    val failures: List<ImageImportFailureTO> = emptyList()
)

/**
 * One image an import could not fetch or store, and why.
 *
 * The reason is the message of the exception that image threw, which is what tells a caller whether
 * it is worth retrying: a 404 will fail the same way forever, whereas a timeout or a missing session
 * may well succeed the next time.
 */
data class ImageImportFailureTO(
    val url: String,
    val reason: String
) {
    constructor(url: String, reason: Throwable) : this(url, reason.message ?: reason.toString())
}
