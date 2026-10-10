package org.endy.pmczero.to

import org.endy.pmczero.model.FailedDownload

/**
 * What a retry of the cached failed downloads did, see
 * [org.endy.pmczero.service.FailedDownloadService.retryAll].
 *
 * Not all-or-nothing, and deliberately so: a cache of two hundred failures will hold some that were a
 * timeout and some that are a 404 and never will be. Failing the whole call on the first of those
 * would leave the rest unretried, so each download is attempted on its own and its outcome reported
 * separately.
 *
 * [downloaded] and [failed] add up to [attempted], which is what makes the call worth repeating: a
 * download that failed again is still in the cache, and one that worked is not.
 */
data class FailedDownloadRunTO(
    /** how many downloads were waiting in the cache */
    val attempted: Int = 0,

    /** how many files were written this time */
    val downloaded: Int = 0,

    /** how many had a file at the path already, so there was nothing left to fetch */
    val skipped: Int = 0,

    /** how many failed again and are still in the cache */
    val failed: Int = 0,

    /**
     * the downloads that failed again, with the reason each one failed this time
     *
     * Capped at [org.endy.pmczero.service.FailedDownloadService.MAX_FAILURES_REPORTED], since a cache
     * of a whole gallery that timed out is a cache of a whole gallery that timed out. [failed] is the
     * real number of them.
     */
    val failures: List<FailedDownloadFailureTO> = emptyList()
)

/**
 * One download that failed again, and why.
 *
 * The url and the path it goes to, since a reason on its own does not say which file of two hundred it
 * was, and the attempts it has had, since a file that has failed five times over is one to stop
 * trying.
 */
data class FailedDownloadFailureTO(
    val url: String,
    val target: String,
    val attempts: Int,
    val reason: String
) {
    constructor(download: FailedDownload) : this(
        url = download.url,
        target = download.target.path,
        attempts = download.attempts,
        reason = download.reason
    )
}