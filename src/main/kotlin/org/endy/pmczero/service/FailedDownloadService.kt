package org.endy.pmczero.service

import org.endy.pmczero.model.FailedDownload
import org.endy.pmczero.to.FailedDownloadFailureTO
import org.endy.pmczero.to.FailedDownloadRunTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Fetches the downloads that failed again, the other end of [FailedDownloads].
 *
 * What it does is deliberately little: it walks the cache, fetches every entry over the
 * [org.endy.pmczero.service.Fetcher] the failed download used, and reports what happened. It creates
 * no medium and writes no row, because the medium of a failed download was recorded by
 * [org.endy.pmczero.model.scraper.MediaAdder] when the scan ran and points at exactly the path this
 * writes: a file that arrives there is a medium that is whole again. A retry of a scan whose media
 * were never saved leaves a file that nothing points at, which is a gap in the database rather than
 * here, and [org.endy.pmczero.service.MsetService] picking the file up in a scan of that location is
 * what closes it.
 *
 * Entries are dropped as they are dealt with rather than at the end, so a run that is interrupted half
 * way does not fetch the same hundred files again next time. A download that failed again stays, with
 * the reason of this attempt and one more on its attempts, since a timeout may well succeed next time
 * and a 404 will not.
 */
@Service
class FailedDownloadService {

    private val logger = LoggerFactory.getLogger(FailedDownloadService::class.java)

    /**
     * Fetches every download waiting in the cache, answering what was written and what failed again.
     *
     * One download that cannot be had does not stop the others: a cache of a gallery of two hundred
     * images holds the two or three that are simply not there beside the ones that timed out, and
     * retrying only the first of those would be a worse answer than retrying all and saying which of
     * them are hopeless.
     *
     * A download whose file is at its path already is dropped and counted as skipped rather than
     * fetched: somebody fetched it in the meantime, whether by hand or by another scrape, and
     * fetching it again would replace a file a medium may have been recorded against.
     *
     * @see FailedDownloads for what is in the cache, which is a process wide thing rather than a bean
     */
    fun retryAll(): FailedDownloadRunTO {
        var downloaded = 0
        var skipped = 0
        var failed = 0
        val failures = mutableListOf<FailedDownloadFailureTO>()

        for (download in FailedDownloads.all()) {
            if (download.target.isFile) {
                FailedDownloads.forget(download)
                skipped++
                continue
            }

            try {
                // no proxy, which is what the download that failed used as well
                download.fetcher.downloadTo(download.url, download.target, withProxy = false)
                FailedDownloads.forget(download)
                downloaded++
            } catch (e: Throwable) {
                // Throwable and not Exception, since the exceptions of this application extend Throwable
                // rather than Exception, see NotFoundException. An Error is not one of those and is not
                // swallowed: an out of memory is the jvm saying it cannot go on, and going on with the
                // next hundred files would only bury that.
                if (e is Error) throw e

                val remembered = FailedDownloads.remember(FailedDownload(download, e))
                failed++
                logger.warn(
                    "the download of ${download.url} failed again (${remembered.attempts} times): " +
                        "${remembered.reason}"
                )
                if (failures.size < MAX_FAILURES_REPORTED) {
                    failures.add(FailedDownloadFailureTO(remembered))
                }
            }
        }

        return FailedDownloadRunTO(
            attempted = downloaded + skipped + failed,
            downloaded = downloaded,
            skipped = skipped,
            failed = failed,
            failures = failures
        )
    }

    companion object {
        /**
         * how many failures the answer names, see [FailedDownloadRunTO.failures]
         *
         * A cap rather than all of them, since a cache of a whole gallery that timed out is a cache of
         * a whole gallery that timed out. The count in [FailedDownloadRunTO.failed] is the real number
         * either way.
         */
        const val MAX_FAILURES_REPORTED = 50
    }
}