package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
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
 * Nothing is skipped: a file that is already there is overwritten. A caller that wants files kept
 * apart needs a [ScanningKontext.locationPath] per run, or a
 * [org.endy.pmczero.model.modern.Location] per gallery.
 *
 * @throws NotAccessibleException when the location is not a file system location, when the path of
 * the element would escape it, or when the download itself fails
 */
@Serializable
@SerialName("fileDownloader")
class FileDownloader : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val root = writableFolderOf(scanningKontext)
        val relative = ScanPath.bessourceNameOf(element, scanningKontext)
        val target = ScanPath.fileIn(root, relative)
            ?: throw NotAccessibleException(
                "the path $relative of $element escapes the location it is downloaded into"
            )

        // no proxy by default: a scan runs against one host, and the page holding the elements was
        // fetched over plain http for a scan that reads it that way
        scanningKontext.fetcher.downloadTo(element, target, withProxy = false)
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
}
