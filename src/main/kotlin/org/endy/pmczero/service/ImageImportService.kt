package org.endy.pmczero.service

import com.microsoft.playwright.options.WaitForSelectorState
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.to.ImageImportFailureTO
import org.endy.pmczero.to.ImageImportTO
import org.endy.pmczero.to.MediumTO
import org.springframework.stereotype.Service
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Imports the images of a web page as media: the files go into a file system location, and a
 * [Medium] with a primary [Bessource] pointing at each of them goes into the database.
 *
 * This is the counterpart of [LocationService.draftMset], which builds the same shape of mset out of
 * a directory that is already on disk. The difference is only where the files come from: there a
 * listing, here a browser that renders the page and hands over the urls it ended up seeing.
 *
 * The database is only written at the very end, and only when [persist] is set, so a run that dies
 * half way leaves files on disk but no rows pointing at them. That is the cheaper of the two
 * leftovers to clean up: a file nobody references is invisible to everything that serves media, while
 * a row pointing at a half written file breaks the url it is served under.
 */
@Service
class ImageImportService(
    private val browserFetcher: BrowserFetcher,
    private val locationService: LocationService,
    private val bessourceRepository: BessourceRepository,
    private val msetService: MsetService
) {

    /**
     * Imports the images of [url] into the location with [locationId].
     *
     * One image that cannot be fetched or written does not stop the others, see [ImageImportTO].
     *
     * @param name the name of the mset, the url when blank
     * @param pattern a regex an image url has to match to be imported, e.g. to take the full size
     * files of a page that also links to thumbnails. Blank takes every image of the page
     * @param scrollTimes how often the page is scrolled before its images are collected, since a
     * lazily loading gallery appends them while scrolling
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application: its images do not exist at the load event, so without one the collection runs
     * against an empty shell and answers no images at all. See [BrowserFetcher.render]
     * @param waitForSelectorState what "appears" has to mean for [waitForSelector] to be satisfied.
     * [com.microsoft.playwright.options.WaitForSelectorState.ATTACHED] on a gallery whose container is
     * empty until the images arrive, since a visible wrapper is a question about the data rather than
     * about the page. See [BrowserFetcher.render]
     * @param persist whether to save the mset. False answers the draft, so a caller can look at what
     * a page holds before anything is stored
     * @param skipExisting whether to leave files alone that are already in the location. True by
     * default, so importing the same gallery twice does not write every image a second time
     * @throws NotAccessibleException when the location cannot receive files, i.e. it is not a
     * writable file system location, when the browser cannot be started, or when [waitForSelector]
     * does not appear
     * @throws org.endy.pmczero.exception.NotFoundException when no location has that id
     */
    fun import(
        locationId: Int,
        url: String,
        name: String? = null,
        pattern: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null,
        waitForSelectorState: WaitForSelectorState? = null,
        persist: Boolean = true,
        skipExisting: Boolean = true
    ): ImageImportTO {
        val location = writableLocation(locationId)
        val storage = location.storageOrNull()
            ?: throw NotAccessibleException("location $locationId has no storage to store media on")
        val storageId = storage.id
            ?: throw NotAccessibleException("location $locationId sits on a storage without id")

        val urls = urlsToImport(url, pattern, scrollTimes, waitForSelector, waitForSelectorState)

        // nothing to import is reported rather than answered as an empty result, so a caller cannot
        // mistake a page whose images never loaded for one that holds none, and is spared an empty set
        if (urls.isEmpty())
            throw NotAccessibleException(
                "no images found on $url, so nothing to import" +
                    (pattern?.takeIf { it.isNotBlank() }?.let { " with the pattern $it" } ?: "")
            )

        // one folder per import, so a second gallery cannot overwrite the files of the first one and
        // the files of a set stay together the way a scanned directory keeps its subdirectories
        val folder = folderFor(name, url)
        val taken = namesAlreadyStored(location, storageId, folder, urls, skipExisting)

        val mset = Mset().apply { this.name = name?.takeIf { it.isNotBlank() } ?: url }
        val failures = mutableListOf<ImageImportFailureTO>()
        // per import call, so a clash is judged against the whole page. See [uniqueFileName], and the
        // scraper counterpart it points at
        val used = mutableSetOf<String>()
        var skipped = 0

        for (imageUrl in urls) {
            // before the `relative in taken` check below, since a variant of a name that is already
            // stored is not that name and so is not skipped: the file is wanted again, only under a
            // name that is free
            val fileName = uniqueFileName(fileNameOf(imageUrl), used)
            val relative = "$folder/$fileName"

            if (relative in taken) {
                skipped++
                continue
            }

            try {
                val target = fileIn(location, relative)
                    ?: throw NotAccessibleException("the path $relative escapes location $locationId")

                browserFetcher.downloadTo(imageUrl, target)

                mset.media.add(mediumOf(fileName, relative, storage, mset))
            } catch (e: Throwable) {
                // Throwable rather than Exception: NotAccessibleException extends Throwable directly,
                // so catching Exception would let a single refused path abort the whole import.
                // Not rethrown either, because one image that cannot be had is not a failure of the
                // call, it is one entry of the failures the caller is told about.
                failures.add(ImageImportFailureTO(imageUrl, e))
            }
        }

        val saved = if (persist && mset.media.isNotEmpty()) msetService.save(mset) else null

        return ImageImportTO(
            locationId = locationId,
            url = url,
            storageId = storageId,
            msetId = saved?.id,
            found = urls.size,
            imported = mset.media.size,
            skipped = skipped,
            failed = failures.size,
            media = mset.media.map { it.toTO() },
            failures = failures
        )
    }

    /**
     * The image urls of [url] that pass [pattern], in the order the page holds them.
     *
     * Blank is not the same as "match everything" here: a pattern that matches everything also drags
     * in the data uris of inline icons and the tracking pixels of the page, which are not images
     * anybody wants on disk. Those are filtered out by scheme instead, so a caller can still filter
     * with a regex without having to know about them.
     */
    private fun urlsToImport(
        url: String,
        pattern: String?,
        scrollTimes: Int,
        waitForSelector: String?,
        waitForSelectorState: WaitForSelectorState?
    ): List<String> {
        val matcher = pattern?.takeIf { it.isNotBlank() }?.toRegex()

        return browserFetcher.imageUrls(url, scrollTimes, waitForSelector, waitForSelectorState)
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .filter { matcher == null || matcher.containsMatchIn(it) }
    }

    /**
     * The relative paths of the files of [folder] that are stored already, so they are not fetched
     * again.
     *
     * Two checks, because either alone is wrong: the database knows the media of a previous import
     * whose files have since been deleted by hand, and the disk knows a file that was put there by
     * the file system scanner and never became a medium. Only what neither knows about is worth
     * downloading.
     */
    private fun namesAlreadyStored(
        location: Location,
        storageId: Int,
        folder: String,
        urls: List<String>,
        skipExisting: Boolean
    ): Set<String> {
        if (!skipExisting) return emptySet()

        val candidates = urls.map { "$folder/${fileNameOf(it)}" }.toSet()

        // one query for the whole page rather than one per image, which is what
        // findNamesOfExistingMedia is there for
        val knownInDatabase = bessourceRepository
            .findNamesOfExistingMedia(storageId, RessType.PRIMARY.i, candidates)
            .toSet()

        // resolved against the location rather than against the working directory: these names are
        // relative to the location root, so reading them from the process directory would ask about
        // a path that does not exist and never match a file
        return candidates.filter { relative ->
            relative in knownInDatabase || fileIn(location, relative)?.isFile == true
        }.toSet()
    }

    /**
     * The location the files are written to, checked for the one thing this import needs it to do.
     *
     * A http location is refused rather than silently ignored: it has no file system path, so there is
     * nowhere to put the bytes, and the alternative of recording media whose files exist nowhere
     * would produce exactly the broken urls the rest of the application is built to avoid.
     */
    private fun writableLocation(locationId: Int): Location {
        val location = locationService.findById(locationId)

        if (location.locationType != LocationType.MAIN_FS.i)
            throw NotAccessibleException(
                "location $locationId is of type ${location.locationType} and so cannot receive " +
                    "downloaded files; a MAIN_FS location is needed"
            )

        if (!locationService.isFileSystemAccessible(locationId))
            throw NotAccessibleException(
                "location $locationId is not an accessible file system location, " +
                    "so no image can be written into it"
            )

        return location
    }

    /**
     * The folder below [location] the files of this import go into, named after [name] or [url].
     *
     * Sanitised down to what a file name may hold, because a page title is free text and may hold
     * anything at all, and the folder is derived from one.
     */
    private fun folderFor(name: String?, url: String): String {
        val raw = name?.takeIf { it.isNotBlank() } ?: url
        return sanitise(raw).ifBlank { "import" }
    }

    /**
     * The [Medium] for the downloaded file [fileName], carrying the bessource that points at it.
     *
     * Wired both ways, medium to set and bessource to medium, the way [MsetService.save] would, so a
     * caller that inspects the draft before persisting it sees the same shape as a persisted one.
     * The type comes from the extension, see [Mtype.of], rather than being fixed to image: a page
     * that links a video or a pdf among its images should not be mislabelled.
     */
    private fun mediumOf(fileName: String, relative: String, storage: Storage, mset: Mset): Medium {
        val medium = Medium().apply {
            name = fileName
            mtype = Mtype.of(fileName).i
            this.mset = mset
        }
        medium.bessources = mutableListOf(
            Bessource().apply {
                name = relative
                ressType = RessType.PRIMARY.i
                this.medium = medium
                this.storage = storage
            }
        )
        return medium
    }

    /**
     * The file name the image at [url] is stored under.
     *
     * The last path segment, without the query, since that is the name the server gave the file. Many
     * galleries name every image after its position (`1.jpg`, `2.jpg`, ...), which is why the caller
     * hands the result through [uniqueFileName] rather than using it as is.
     *
     * Percent escapes are decoded, so a name like `das%20Bild.jpg` does not end up on disk with the
     * escapes in it, and everything a file name may not hold becomes an underscore.
     */
    private fun fileNameOf(url: String): String {
        val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(last, StandardCharsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            // a stray '%' that is not an escape; the raw name is still a usable one
            last
        }
        val name = sanitise(decoded)
        return name.ifBlank { "image" }
    }

    /**
     * [fileName], or the first variant of it that is not in [used] yet.
     *
     * The counter goes in front of the extension, so the name keeps saying what it is: `bild.1.jpg`
     * rather than `bild.jpg.1`. Variants rather than a single counter per run, so a gallery that
     * repeats a name twice gets two files and a gallery that has none at all is unaffected.
     *
     * The same thing is done for the scraper pipeline, see
     * [org.endy.pmczero.model.scraper.ScanPath.firstFreeVariantOf], which is deliberately kept
     * separate rather than shared:
     *
     * - this one is a set of names for the whole [import] call, and [used] is created per call at the
     *   top of it. It can be a set because only this service ever allocates a name.
     * - the scraper one is keyed by the element it was derived from, in
     *   [org.endy.pmczero.model.ScanningKontext.takenFileNames]. That is what lets the two workers of
     *   a scraper that both need a name,
     *   [org.endy.pmczero.model.scraper.MediaAdder] and [org.endy.pmczero.model.scraper.FileDownloader],
     *   ask for the same element and get the same answer, rather than the second one believing the
     *   first had already claimed a name.
     *
     * So the two cannot be merged without giving this service a kontext to hold its state in, which is
     * more coupling than ten lines of counter are worth. When one of them changes, change both: they
     * have to agree on what a clash is called, since a scan and an import that name the same file
     * differently would each overwrite what the other wrote.
     */
    private fun uniqueFileName(fileName: String, used: MutableSet<String>): String {
        if (used.add(fileName)) return fileName

        val stem = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")

        var counter = 1
        while (!used.add("$stem.$counter.$extension")) counter++

        return "$stem.$counter.$extension"
    }

    /**
     * [text] reduced to what a single file name may hold: no separators, no reserved characters, no
     * trailing dot.
     *
     * Applied to a page title and to every file name taken from a url, since both are free text that
     * ends up on the file system. Windows additionally refuses the reserved device names, which is
     * why `CON` and its relatives are replaced as well rather than left to fail the write.
     */
    private fun sanitise(text: String): String {
        val cleaned = text
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('.', ' ')

        return if (RESERVED.any { cleaned.equals(it, ignoreCase = true) }) "_$cleaned" else cleaned
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..`: the folder name comes from a
     * page title and the file name from a url, so neither can be trusted to stay inside.
     */
    private fun fileIn(location: Location, relative: String): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = File(root, relative).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    private companion object {

        /** the device names windows refuses to use as a file name, whatever the extension */
        val RESERVED = setOf("con", "prn", "aux", "nul", "com1", "com2", "lpt1", "lpt2")
    }
}
