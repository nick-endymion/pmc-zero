package org.endy.pmczero.ressource

import org.endy.pmczero.service.FailedDownloadService
import org.endy.pmczero.to.FailedDownloadRunTO
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The files a scrape could not fetch.
 *
 * Its own resource rather than a method on [ScraperRessource], since nothing here is about scraping a
 * page: these calls come after a run is over and know nothing about the scraper that ran, and the url
 * that fetched them is about files rather than about pages.
 */
@RestController
@RequestMapping("/api/downloads")
class DownloadRessource(private val failedDownloadService: FailedDownloadService) {

    /**
     * Fetches every download of this application that failed and is still waiting in
     * [org.endy.pmczero.service.FailedDownloads], answering what was written and what failed again.
     *
     * A scrape that fails on one image of two hundred does not stop there:
     * [org.endy.pmczero.model.scraper.FileDownloader] keeps what it could not fetch, the run reports
     * the failures on its kontext and is over, and this is what a caller reaches for when it wants
     * those files: a timeout on a busy server, a connection dropped halfway through a gallery, an
     * image that was behind a session and has become reachable.
     *
     * Nothing is created for a file that arrives. The medium of a failed download was recorded when the
     * scan ran and points at exactly the path this writes, so a file that lands there is a medium that
     * is whole again. A scrape whose media were never saved leaves a file that nothing points at, which
     * is a gap in the database rather than here: a scan of that location picks it up.
     *
     * Safe to call as often as one likes, and worth calling as often as one likes: a download that
     * fails again stays in the cache with the reason of this attempt and one more on its attempts, and
     * one whose file is already at the path is dropped as done rather than fetched again. A file that
     * has failed over and over says so in the answer, which is how a caller tells a server that is busy
     * from a file that is not there.
     *
     * The cache is a process wide thing rather than a table, so what is in it is what this application
     * has failed since it was started and nothing older. Restarting it forgets the failures of the runs
     * before, which are still reported in the result of those runs.
     */
    @PostMapping("/retry-failed")
    fun retryFailedDownloads(): FailedDownloadRunTO {
        return failedDownloadService.retryAll()
    }
}