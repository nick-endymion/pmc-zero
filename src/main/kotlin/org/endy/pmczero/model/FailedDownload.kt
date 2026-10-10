package org.endy.pmczero.model

import org.endy.pmczero.service.Fetcher
import java.io.File
import java.time.LocalDateTime

/**
 * One download of this application that failed and is kept for another try.
 *
 * A row of a cache and not of a table, see [org.endy.pmczero.service.FailedDownloads]: a download
 * that fails is usually a timeout or a server that was busy for a moment, and what the caller wants
 * with it is to fetch it again in a minute, not to keep a record of it forever. What is kept is
 * everything an attempt needs and nothing about the scan it came from:
 *
 * - [url] and [target], so the attempt writes to the file the scan had already decided on. That path
 *   may well be a variant like `a.1.jpg`, so it is the path rather than the name of the medium: a
 *   retry that recomputed the name would write a second file for a medium that points at the first.
 * - [fetcher], so the retry goes over the same road the download did. A failure that needs the
 *   session of a browser is not fixed by asking a plain http client, and a failure of one is fixed by
 *   asking the other just as little.
 * - [reason] and [failedAt], which are what tell a caller whether another try is worth anything: a
 *   404 will fail the same way forever, whereas a timeout may well succeed next time.
 * - [attempts], so a file that has failed five times over as many runs is recognisable as one that is
 *   simply not there.
 *
 * No mset, no medium and no location: the file is written to the path that was asked for, and the
 * medium that points at it was recorded when the scan ran. A retry therefore needs nothing from the
 * database, and equally fixes nothing in it: a download that failed for a scan whose media were never
 * saved leaves a file on disk that nothing points at, which [org.endy.pmczero.service.MsetService] can
 * pick up in a scan of that location.
 */
data class FailedDownload(
    val url: String,

    /** where the file of this download goes, decided by the scan that tried it */
    val target: File,

    /** how the download is fetched, which is a property of the scan and not of this cache */
    val fetcher: Fetcher,

    /** the message of what was thrown, see [ScanFailure.reason] */
    val reason: String,

    /** when it was tried, since a reason of a week ago says less than one from a minute ago */
    val failedAt: LocalDateTime,

    /** how many times it has been tried and failed, one for the first */
    val attempts: Int
) {
    constructor(url: String, target: File, fetcher: Fetcher, failure: Throwable) : this(
        url = url,
        target = target,
        fetcher = fetcher,
        reason = failure.message ?: failure.toString(),
        failedAt = LocalDateTime.now(),
        attempts = 1
    )

    /** the same download failed again, with the reason of this try */
    constructor(previous: FailedDownload, failure: Throwable) : this(
        url = previous.url,
        target = previous.target,
        fetcher = previous.fetcher,
        reason = failure.message ?: failure.toString(),
        failedAt = LocalDateTime.now(),
        attempts = previous.attempts
    )

    /**
     * what tells one cached download from another: the url and the file it goes to
     *
     * Both, since the same url is fetched into a different file for every scan, and one entry per scan
     * of one gallery is what a caller wants to retry rather than one entry of the gallery.
     */
    val key: Pair<String, String> get() = url to target.path
}