package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Downloads the element it is handed into the folder the [ScanningKontext] points at.
 *
 * Where [MediaAdder] records an element and [StructuredWorker] fetches a page, this is the worker
 * that puts the bytes on disk: the element is taken to be the url of a file, and that file is
 * written into [ScanningKontext.locationPath] below [ScanningKontext.location]. It records nothing,
 * so it composes with [MediaAdder] rather than replacing it: a scraper that runs both against the
 * same element ends up with a medium whose bessource and a file that actually exist.
 *
 * Both halves are needed for the result to be reachable. A medium without the file has a url that
 * answers 404, and a file without the medium is invisible to everything that serves media.
 *
 * The download goes through [org.endy.pmczero.service.Fetcher], i.e. through the kontext rather than
 * through a [org.endy.pmczero.service.Downloader] of its own, so a scan that renders its pages in a
 * browser fetches its files the same way. A page that hands out urls only its own cookies reach
 * would otherwise yield media whose files could not be fetched at all.
 *
 * The name of the written file is the last path segment of the url, without its query, sanitised down
 * to what a single file name may hold. A folder of its own is not derived from the url: the element
 * is stored as one file of [ScanningKontext.locationPath], which is what keeps the files of a scan
 * together the way the subdirectories of a scanned directory do.
 *
 * Nothing is recorded and nothing is skipped: no medium is created, and a file that is already there
 * is overwritten. Recording is [MediaAdder]'s job and deliberately not folded in here, since a
 * worker that both writes and records cannot be told apart from a scan that writes without
 * recording, which is exactly the state in which a file on disk is invisible to everything that
 * serves media. A caller that wants files kept apart needs a [ScanningKontext.locationPath] per run,
 * or a [org.endy.pmczero.model.modern.Location] per gallery.
 *
 * @throws NotAccessibleException when the location is not a file system location, when
 * [ScanningKontext.locationPath] would escape it, or when the download itself fails
 */
@Serializable
@SerialName("fileDownloader")
class FileDownloader : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val root = writableFolderOf(scanningKontext)
        val relative = relativeNameOf(element, scanningKontext.locationPath)
        val target = fileIn(root, relative)
            ?: throw NotAccessibleException(
                "the path $relative of $element escapes the location it is downloaded into"
            )

        // no proxy by default: a scan runs against one host, and the page holding the elements was
        // fetched over plain http for a scan that reads it that way
        scanningKontext.fetcher.downloadTo(element, target, withProxy = false)
    }

    /**
     * The folder the files of this scan go into, i.e. [ScanningKontext.locationPath] below
     * [ScanningKontext.location], checked for the things that make it a usable target.
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

    /**
     * The path of [element] below [locationPath], i.e. the file name inside the folder of the scan.
     *
     * Separators are normalised to `/` before the path is split, because a [ScanningKontext] carries
     * a bessource style path that a caller may well have built on windows, where it would otherwise
     * become part of a single file name on a posix system rather than directory boundaries.
     *
     * Blank is the location root, tested with [String.isBlank] rather than [String.isNotEmpty] so a
     * path of spaces does not turn into a folder literally named `"   "`.
     */
    private fun relativeNameOf(element: String, locationPath: String): String {
        val folder = locationPath.replace('\\', '/').trim('/')
        val name = fileNameOf(element)

        return if (folder.isBlank()) name else "$folder/$name"
    }

    /**
     * The name the file at [element] is written under: the last segment of its path, without the
     * query or the fragment.
     *
     * Percent escapes are decoded and everything a single file name may not hold becomes an
     * underscore, since both come from a url, i.e. from text the scanned page had a say in. The
     * separators are gone by then, which is what keeps a url ending in `/..` or carrying a nested
     * path from naming anything outside the folder of the scan.
     *
     * A url with no usable last segment, one that ends in a slash or is only a query, gets a name
     * from its host rather than none at all: the download still happened, and a file the scan
     * cannot name is a file nothing can refer to later.
     */
    private fun fileNameOf(element: String): String {
        val withoutQuery = element.substringBefore('?').substringBefore('#')
        val last = withoutQuery.substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(last, StandardCharsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            // a stray '%' that is not an escape; the raw name is still a usable one
            last
        }

        val name = sanitise(decoded)
        if (name.isNotBlank()) return name

        val host = sanitise(runCatching { java.net.URL(element).host }.getOrDefault(""))
        return host.ifBlank { "downloaded" }
    }

    /**
     * [relative] inside [root], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks. The file name
     * cannot hold a separator any more, since it has been sanitised, but
     * [ScanningKontext.locationPath] is free text a caller sets, so the folder part is not to be
     * trusted either.
     */
    private fun fileIn(root: File, relative: String): File? {
        val file = File(root, relative).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    /** [text] reduced to what a single file name may hold: no separators, no reserved characters */
    private fun sanitise(text: String): String =
        text.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('.', ' ')
}
