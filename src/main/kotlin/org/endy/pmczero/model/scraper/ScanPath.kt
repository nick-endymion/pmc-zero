package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Where the file of a scanned [element] belongs, as a path relative to the
 * [ScanningKontext.location] of the scan that found it.
 *
 * Shared by the two workers that have to agree on it: [MediaAdder] records that path as the name of a
 * [org.endy.pmczero.model.modern.Bessource], and [FileDownloader] writes the file to it. When the two
 * disagreed about the name, a medium would point at a file that is not there, which is a url that
 * answers 404 with nothing in the logs to say why. So they do not each decide, they both ask here.
 *
 * That name is what [org.endy.pmczero.service.LocationService.url] builds a url from, so it has to
 * be relative to the location: a bessource named `2020/august/a.jpg` on the location `/srv/media` is
 * served as `/srv/media/2020/august/a.jpg`.
 */
internal object ScanPath {

    /**
     * The name of the bessource of [element], i.e. its path below the location of the scan.
     *
     * The helpers below are internal rather than private so a worker added later has one place to ask
     * rather than a second implementation to keep in step with this one.
     *
     * [ScanningKontext.locationPath] prefixes it, so the files of a scan land in the folder the scan
     * was given rather than in the location root, and stay together there.
     *
     * How the rest of the name is built depends on what kind of location this is, because the two
     * hold entirely different things:
     *
     * - an FS location holds files, and the file of [element] is named after the element. The element
     *   is a remote url whose path is not below the folder of the location at all, so there is no
     *   part of it to strip.
     * - an http location is itself served out of a url, so the name is the part of the element below
     *   the uri of the location. Nothing is downloaded in that case: the element is already the url of
     *   the file, and the name only has to say which part of the location it sits in. See
     *   [rightPartOf] for the element that is not below the location at all.
     *
     * The name of a clash is only varied for an FS location. There the name is a file, and two elements
     * reducing to the same one would otherwise overwrite each other. On a http location it is a url
     * fragment instead, so two such names are two names for what is served at one url, and suffixing
     * the second would invent a file nothing has.
     *
     * The catchup location of a scan that was not assigned one has no location type and an empty uri,
     * and is answered like an http one. That is what keeps the whole url as the name, which is what a
     * scan with no storage has to record: there is no file for it to point at, only the place it was
     * found.
     */
    fun bessourceNameOf(element: String, scanningKontext: ScanningKontext): String {
        val location = scanningKontext.location

        val name = if (location.isFileSystem())
            freeFileNameOf(element, scanningKontext)
        else
            rightPartOf(element, location)

        return join(folderOf(scanningKontext.locationPath), name)
    }

    /**
     * [fileNameOf] of [element], or a variant of it when the caller asked for one and the scan
     * already handed that name out.
     *
     * A gallery that names its images `1.jpg`, `2.jpg`, ... never comes through here. A page that links
     * the same name twice, or links a file and then a thumb under the same name, does, and it is
     * [ScanningKontext.alwaysNewDownload] that decides what happens to it: with that set, the second
     * gets a variant of the name so the second download cannot replace the first and leave the medium
     * of the first pointing at bytes that are no longer the ones it was recorded for. Without it, both
     * keep the plain name and [FileDownloader] fetches the second one only if the first is not there,
     * which is the same answer for a gallery imported a second time.
     *
     * The name is remembered against the element, so the same element always answers the same name.
     * That is what makes [MediaAdder] and [FileDownloader] agree even though both ask: without it the
     * second one to ask would see the first name as taken and pick the next variant, leaving a
     * bessource pointing at `a.1.jpg` and a file written to `a.jpg`.
     *
     * Only the file name is varied, never the folder of the scan, so a variant stays next to what it
     * collided with.
     *
     * The counterpart of this on the other import path is
     * [org.endy.pmczero.service.ImageImportService.uniqueFileName], which names a clash the same way.
     * It differs in what it keeps: a set of names for one `import` call, against the element keyed map
     * of [ScanningKontext.takenFileNames] here, which is what lets two workers agree.
     */
    private fun freeFileNameOf(element: String, scanningKontext: ScanningKontext): String {
        val taken = scanningKontext.takenFileNames

        taken[element]?.let { return it }

        val name = fileNameOf(element)

        // a variant only when the caller asked for one, see [ScanningKontext.alwaysNewDownload].
        // Otherwise the name a file already sits under is the name this element keeps, since the
        // downloader leaves that file alone rather than writing a second copy beside it: a name of its
        // own would point at a file that is not there, which is what a medium must never do
        val free = if (scanningKontext.alwaysNewDownload &&
            (taken.values.contains(name) || existingOnDisk(scanningKontext, name))
        ) taken.values.toList().firstFreeVariantOf(name)
        else name

        taken[element] = free
        return free
    }

    /**
     * The first variant of [clashed] that is not in this list, i.e. of the names handed out so far.
     *
     * The counter goes in front of the extension, so the name keeps saying what it is: `bild.1.jpg`
     * rather than `bild.jpg.1`. Variants rather than a single counter per run, so a page that clashes
     * three times gets three files and a page that never clashes is unaffected.
     *
     * The same thing is done for the other import path,
     * [org.endy.pmczero.service.ImageImportService.uniqueFileName], which is deliberately kept
     * separate rather than shared:
     *
     * - that one is a set of names for a whole `import` call, created at the top of the call, and it
     *   can be a set of plain names because only [org.endy.pmczero.service.ImageImportService] ever
     *   allocates one there.
     * - this one works on the names keyed by their element in
     *   [ScanningKontext.takenFileNames], see [freeFileNameOf], because two workers of a scraper need
     *   the same answer for one element and neither of them is the only one asking.
     *
     * When one of them changes, change both: a scan and an import that named the same file differently
     * would each overwrite what the other wrote.
     */
    private fun List<String>.firstFreeVariantOf(clashed: String): String {
        val used = this.toMutableSet()
        val stem = clashed.substringBeforeLast('.', clashed)
        val extension = clashed.substringAfterLast('.', "")

        var counter = 1
        while (!used.add("$stem.$counter.$extension")) counter++

        return "$stem.$counter.$extension"
    }

    /**
     * Whether a file of [name] is already in the folder of the scan, so a second import of the same
     * gallery does not hand the new medium the name the old file already occupies.
     *
     * Only the folder of the scan is looked at, since that is where the file would go. False for a
     * location that holds no files, since there nothing is written and so nothing can be in the way.
     *
     * The other import path does not need this, since it looks at the disk before it allocates any
     * name: [org.endy.pmczero.service.ImageImportService.namesAlreadyStored] collects the stored paths
     * up front and skips those images. A scan has no such step, so the check is here.
     */
    private fun existingOnDisk(scanningKontext: ScanningKontext, name: String): Boolean {
        val uri = scanningKontext.location.uri?.takeIf { it.isNotBlank() } ?: return false
        if (!scanningKontext.location.isFileSystem()) return false

        return File(File(uri, folderOf(scanningKontext.locationPath)), name).isFile
    }

    /** The name a single file of [element] may hold, taken from the url it was found under. */
    fun fileNameOf(element: String): String {
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
     * [ScanningKontext.locationPath] as a folder to prefix a name with, empty when there is none.
     *
     * Separators are normalised to `/`, because a bessource name is `/` separated on every platform
     * and a path a caller built on windows would otherwise become part of a single file name on a
     * posix system rather than directory boundaries. Leading and trailing slashes are dropped so that
     * `"/2020/august/"` and `"2020/august"` do not produce two different names for one folder.
     *
     * Blank rather than null for "the location root", so no caller has to decide between a root and a
     * named folder at every use. Tested with [String.isBlank] rather than [String.isNotEmpty], so a
     * path of spaces does not become a folder literally named `"   "`.
     */
    fun folderOf(locationPath: String): String =
        locationPath.replace('\\', '/').trim('/').let { if (it.isBlank()) "" else it }

    /**
     * [name] below [folder], or on its own when there is no folder.
     *
     * A leading slash on [name] is dropped, since that is what
     * [org.endy.pmczero.model.modern.Location.getRightPart] answers for a http location whose uri ends
     * in one: the part of `http://example.org/main/a.pdf` below `http://example.org/main` is
     * `/a.pdf`, and joining that to a folder as is would give `2020/august//a.pdf`, which is a name
     * no other part of the application builds and which
     * [org.endy.pmczero.service.LocationService.url] would turn into a url with a double slash in it.
     */
    fun join(folder: String, name: String): String {
        val clean = name.trimStart('/')

        return if (folder.isEmpty()) clean else "$folder/$clean"
    }

    /**
     * The name of the medium that stands for the file at [bessourceName], i.e. its last segment
     * without the query and the fragment.
     *
     * The bessource name keeps the query for an http location, since there it is part of the url the
     * file is served under and dropping it would point at a different file. The name of a medium is
     * not a url but something a person reads in a listing, where `viertes.jpg?size=large` says less
     * about the file than `viertes.jpg` does and nothing more.
     *
     * The folders are dropped as well, so two galleries holding an `a.jpg` each are two equally named
     * media rather than one name per folder.
     */
    fun mediumNameOf(bessourceName: String): String =
        bessourceName.substringAfterLast('/').substringBefore('?').substringBefore('#')

    /**
     * [relative] inside [root], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks. A bessource name
     * holds a sanitised file name but a free text folder, so the whole of it is not to be trusted.
     */
    fun fileIn(root: File, relative: String): File? {
        val file = File(root, relative).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    private fun Location.isFileSystem(): Boolean =
        locationType == LocationType.MAIN_FS.i || locationType == LocationType.TN_FS.i

    /**
     * The part of [element] below the uri of [location], i.e.
     * [org.endy.pmczero.model.modern.Location.getRightPart] with the mismatch it throws on handled.
     *
     * That function answers a bare [Exception] with no message when the element does not start with
     * the uri of the location, and reads past the end of the element when it is shorter than the uri.
     * Neither says what was wrong, and both reach a caller as a failure of a whole scan over one
     * element.
     *
     * So the element is used whole when it does not sit below the location. That is the honest answer
     * for the case this runs into: a page that links a file from somewhere else, an element the
     * location knows nothing about. Whether that file is reachable is decided by whoever asks for its
     * url, not here, and a name that at least points at the real url is more use than a crash.
     */
    private fun rightPartOf(element: String, location: Location): String {
        val uri = location.uri

        if (uri.isNullOrBlank() || element.length < uri.length) return element

        return if (element.startsWith(uri)) element.substring(uri.length) else element
    }

    /**
     * [text] reduced to what a single file name may hold: no separators, no reserved characters.
     *
     * Applied to a file name taken from a url, i.e. to text the scanned page had a say in. The
     * separators are gone by the time this returns, which is what keeps a url ending in `/..` or
     * carrying a nested path from naming anything outside the folder of the scan.
     */
    fun sanitise(text: String): String =
        text.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('.', ' ')
}
