package org.endy.pmczero.service

import org.endy.pmczero.model.FailedDownload
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The downloads of this application that failed and are waiting for another try.
 *
 * A process wide cache and not a table, and not a bean either. Both are worth a word:
 *
 * - **not a table** because this is a cache of transient failures. A timeout or a server that was busy
 *   for a moment is worth fetching again in a minute; a row for it would have to be cleaned up, would
 *   have to grow, and would still be worthless after a restart that moved every file. What is lost on
 *   a restart is the retry list of the last run, and the failures themselves are not lost: they are
 *   reported in [org.endy.pmczero.model.ScanningKontext.failures] of the run that had them.
 * - **not a bean** because nothing can hand it to the one that fills it. [org.endy.pmczero.model.scraper.FileDownloader]
 *   is part of a scraper and is built by kotlinx.serialization when a stored scraper is read back,
 *   which offers no place to inject anything, and every scan deserializes its own copy. A bean would
 *   therefore have to be reached through a static anyway, which is this with the indirection left off.
 *   The retry that reads it, [FailedDownloadService], is an ordinary bean.
 *
 * Keyed by url and target together, see [FailedDownload.key], since one url is fetched into a
 * different file for every scan. A download that fails again is the same entry with a higher
 * [FailedDownload.attempts] and a newer reason rather than a second one, so a gallery of two hundred
 * images whose import failed five times over is two hundred entries and not a thousand.
 *
 * It grows with the number of downloads that failed and is never emptied on its own. That is
 * deliberate rather than an oversight: what a caller does about a failure is call
 * [FailedDownloadService.retryAll], which drops what it fetched, and the entries that are left are
 * the ones that never worked. For an application whose failures are files it tried to import, that
 * number is bounded by what it imported; a caller that wants a bound of its own on it can ask for one,
 * and nothing here would have to change for that beyond a size check on [remember].
 *
 * @see FailedDownloadService for the endpoint that retries what is in here
 */
object FailedDownloads {

    private val logger = LoggerFactory.getLogger(FailedDownloads::class.java)

    private val failed = ConcurrentHashMap<Pair<String, String>, FailedDownload>()

    /**
     * Keeps [download] for another try, or counts the attempt on the one that is already there.
     *
     * Counting rather than replacing is what makes [FailedDownload.attempts] worth anything: a file
     * that has failed over and over is a different thing to retry than one that failed once.
     */
    fun remember(download: FailedDownload): FailedDownload =
        failed.compute(download.key) { _, older ->
            download.copy(attempts = (older?.attempts ?: 0) + 1)
        }!!

    /** everything waiting for another try, in no particular order and with a copy of the list */
    fun all(): List<FailedDownload> = failed.values.toList()

    /** how many downloads are waiting, for an answer that is a count and not a list */
    fun count(): Int = failed.size

    /** the entry of [download], null when there is none */
    fun find(download: FailedDownload): FailedDownload? = failed[download.key]

    /**
     * Drops [download], because it was fetched or because there is nothing left to fetch.
     *
     * By key and not by equality, so a caller holding an older copy of the entry still removes the
     * one that is there now.
     */
    fun forget(download: FailedDownload): Boolean {
        val dropped = failed.remove(download.key) != null
        if (dropped) logger.info("dropped the cached download of ${download.url}: ${download.reason}")
        return dropped
    }

    /** empties the cache, which is what a test and nothing else has a reason to ask for */
    fun clear() {
        failed.clear()
    }
}