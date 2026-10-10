package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.FailedDownload
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanFailure
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.service.FailedDownloads
import java.io.File

/**
 * Downloads the element it is handed into the folder the [ScanningKontext] points at.
 *
 * Where [MediaAdder] records an element and [StructuredWorker] fetches a page, this is the worker
 * that puts the bytes on disk: the element is taken to be the url of a file, and that file is
 * written into [ScanningKontext.locationPath] below [ScanningKontext.location]. It records nothing,
 * so it composes with [MediaAdder] rather than replacing it: a scraper that runs both against the
 * same element ends up with a medium and the file its bessource points at.
 *
 * Both halves are needed for the result to be reachable. A medium without the file has a url that
 * answers 404, and a file without the medium is invisible to everything that serves media. The path
 * is not decided here but asked of [ScanPath], the same answer [MediaAdder] records, so the two
 * cannot drift apart.
 *
 * The download goes through [org.endy.pmczero.service.Fetcher], i.e. through the kontext rather than
 * through a [org.endy.pmczero.service.Downloader] of its own, so a scan that renders its pages in a
 * browser fetches its files the same way. A page that hands out urls only its own cookies reach
 * would otherwise yield media whose files could not be fetched at all.
 *
 * ### A file that is already there
 *
 * By default it is left alone: the element is recorded in `failures` with the reason the file was not
 * fetched again, and nothing is written. The medium still points at the file that is there, so a second
 * import of a gallery adds no files and says which ones it did not fetch, rather than filling the
 * location with copies of a gallery that is already there. A caller that does want the copies asks for
 * them, see [ScanningKontext.alwaysNewDownload], and then the file is written beside the old one under
 * a name of its own and the two are kept apart.
 *
 * A caller that wants two runs of a gallery kept apart altogether needs a [ScanningKontext.locationPath]
 * per run, or a [org.endy.pmczero.model.modern.Location] per gallery: the names this worker answers are
 * the names of the files in the folder of the run, so a different folder is a different answer.
 *
 * ### A download that failed
 *
 * Kept for another try in [FailedDownloads], with the url and the path it goes to, before the failure
 * is thrown. A run reports its failures on the [ScanningKontext] and is then over, and the url of an
 * image that timed out is nowhere to be found again after that: a caller who wants it has nothing to
 * work with. [org.endy.pmczero.service.FailedDownloadService] is the other end of that, and it fetches
 * them again over the same [org.endy.pmczero.service.Fetcher] the run used.
 *
 * The two paths that return rather than fetch are not failures and are not kept: an excluded download
 * is one that was not to be fetched at all, and a file that is already there is one that does not need
 * fetching. What is kept is a download that was tried and could not be done.
 *
 * @throws NotAccessibleException when the location is not a file system location, when the path of
 * the element would escape it, or when the download itself fails
 */
@Serializable
@SerialName("fileDownloader")
class FileDownloader : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        // a scan that was told to leave the files where they are records the element and says why,
        // rather than fetching it and staying quiet about it. Reported the same way a download that
        // failed is, since a caller reads both out of the same list and cannot act on one of them any
        // differently: see [org.endy.pmczero.to.ImageImportTO.failures]
        if (scanningKontext.skipDownloads) {
            scanningKontext.failures.add(ScanFailure(element, DOWNLOAD_EXCLUDED))
            return
        }

        val root = writableFolderOf(scanningKontext)
        val relative = ScanPath.bessourceNameOf(element, scanningKontext)
        val target = ScanPath.fileIn(root, relative)
            ?: throw NotAccessibleException(
                "the path $relative of $element escapes the location it is downloaded into"
            )

        // a file that is already there is left where it is, and said so, unless the caller asked for a
        // new copy of it. The name the medium was recorded under is the name of that file, so there is
        // nothing to write and nothing to rename: a second import of a gallery adds no files and
        // reports one entry per file it did not fetch, the way it reports one per file that 404s
        if (!scanningKontext.alwaysNewDownload && target.isFile) {
//            scanningKontext.failures.add(ScanFailure(element, FILE_ALREADY_STORED)) // not reasonable to report this as a failure, since the file is there and the medium points at it
            return
        }

        // no proxy by default: a scan runs against one host, and the page holding the elements was
        // fetched over plain http for a scan that reads it that way
        //
        // kept for another try before it is thrown, since a download that failed is otherwise gone:
        // the kontext reports it and then the run is over, and the url of a 200 image that timed out
        // is nowhere to be found again. The two paths that returned above are not failures and are not
        // kept: an excluded file is not to be fetched at all, and a file that is already there is one
        // that does not need fetching.
        try {
            scanningKontext.fetcher.downloadTo(element, target, withProxy = false)
        } catch (e: Throwable) {
            // Throwable and not Exception, since the exceptions of this application extend Throwable
            // rather than Exception, see NotFoundException. Rethrown either way, so this adds a retry to
            // a failure rather than swallowing it: a scrape whose downloads fail is a scrape that
            // stops, and RecoveryWorker is what decides otherwise. An Error is not kept, being a jvm
            // that cannot go on rather than a file that could not be had.
            if (e !is Error) {
                FailedDownloads.remember(FailedDownload(element, target, scanningKontext.fetcher, e))
            }
            throw e
        }
    }

    /**
     * The folder the files of this scan go into, checked for the things that make it a usable target.
     *
     * Blank [ScanningKontext.location.uri] is refused rather than defaulted. It is what the catchup
     * location of an unassigned scan carries, and `File("")` resolves to the working directory of
     * the process, so a scan without a location would quietly fill the folder the application is
     * started in instead of failing.
     */
    private fun writableFolderOf(scanningKontext: ScanningKontext): File {
        val location = scanningKontext.location

        if (location.locationType != LocationType.MAIN_FS.i && location.locationType != LocationType.TN_FS.i)
            throw NotAccessibleException(
                "location ${location.id} is of type ${location.locationType} and so cannot receive " +
                    "downloaded files; a file system location is needed"
            )

        val uri = location.uri?.takeIf { it.isNotBlank() }
            ?: throw NotAccessibleException("location ${location.id} has no path to download into")

        val root = File(uri).canonicalFile

        if (!root.isDirectory || !root.canWrite())
            throw NotAccessibleException("the path $uri of location ${location.id} is not a writable directory")

        return root
    }

    private companion object {
        /**
         * Why an element was not downloaded, when the scan was told to leave the files where they are.
         *
         * One constant rather than the text at the place it is written, since a caller reads this out
         * of `ImageImportTO.failures` to decide whether the file is still to be had, and a wording
         * change is then something it has to recognise.
         */
        const val DOWNLOAD_EXCLUDED = "Download excluded. Need to be done manually"

        /**
         * Why an element was not downloaded, when the file of that medium is already in the location.
         *
         * A different text from [DOWNLOAD_EXCLUDED] on purpose, since the two mean opposite things to
         * a caller: here the file is there and nothing is missing, whereas an excluded file still has
         * to be fetched. Both are reported in `ImageImportTO.failures` rather than in a count of their
         * own, so the reason is the only thing that tells them apart.
         */
        const val FILE_ALREADY_STORED = "File already exists, so it was not downloaded"
    }
}
