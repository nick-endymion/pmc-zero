package org.endy.pmczero.to

/**
 * What a run of [org.endy.pmczero.service.FileInfoService.recordMissingOf] did to one storage.
 *
 * Not all-or-nothing, and deliberately so, for the same reason [ImageImportTO] is not: a storage of
 * fifty thousand files will hold some whose file has been deleted since, some on a location that is
 * not reachable right now, and some that are not files at all. Failing the whole call on the first of
 * those would leave the other forty-nine thousand unrecorded.
 *
 * [recorded] and [failed] add up to [attempted], which is what makes the run repeatable: everything in
 * [failed] is safe to try again, and [skipped] is not, since a row that is there is a row that is
 * believed to be true of its file.
 */
data class FileInfoRunTO(
    /** the storage that was walked */
    val storageId: Int,

    /** the bessources of the storage that had no row yet, i.e. what was attempted */
    val attempted: Int = 0,

    /** how many rows were written */
    val recorded: Int = 0,

    /** how many were there already, so nothing was written for them */
    val skipped: Int = 0,

    /** how many could not be determined */
    val failed: Int = 0,

    /**
     * the bessources that failed, with the reason each one did, capped at
     * [org.endy.pmczero.service.FileInfoService.MAX_FAILURES_REPORTED] of them
     *
     * Capped because [failed] may be the whole of a storage and a response has to stay a response.
     * [failed] is the real number of them.
     */
    val failures: List<FileInfoFailureTO> = emptyList()
)

/**
 * One bessource whose file could not be looked at, and why.
 *
 * The reason is the message of the exception that bessource threw, which is what tells a caller
 * whether it is worth retrying: a file that is not there will fail the same way forever, whereas a
 * location that was unreachable for a moment may well be there next time.
 */
data class FileInfoFailureTO(
    val bessourceId: Int,
    val reason: String
) {
    constructor(bessourceId: Int, reason: Throwable) : this(bessourceId, reason.message ?: reason.toString())
}