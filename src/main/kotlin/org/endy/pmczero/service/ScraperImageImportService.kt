package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.FoundElementsWorker
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.ScanFormat
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.model.scraper.Worker
import org.endy.pmczero.to.FoundElementTO
import org.endy.pmczero.to.ImageImportFailureTO
import org.endy.pmczero.to.ImageImportTO
import org.endy.pmczero.to.ImageListTO
import org.springframework.stereotype.Service

/**
 * Imports the images of a web page by running a [Scraper] over it, the counterpart of
 * [ImageImportService.import], which does the same work without the scraper pipeline.
 *
 * The two sit side by side because they answer different questions. [ImageImportService] collects the
 * image urls out of the rendered dom itself and downloads them; this one hands the dom to a scraper and
 * lets the parsers and workers of the pipeline pick it apart. So this is the one to reach for when what
 * a page offers has to be expressed as a parser, e.g. to take the links of a page rather than its
 * images, or to read a `data-` attribute, or to narrow the elements with a css selector. It is also the
 * path that a stored scanner goes through, see [org.endy.pmczero.service.ScannerService], so a scraper
 * worth keeping belongs there rather than here.
 *
 * The scraper of this call is built by [scraperOf] rather than loaded from the database, since there is
 * nowhere to store one yet, the same reason [org.endy.pmczero.ressource.ScraperRessource.draft] builds
 * its own. It is an ordinary [Scraper] all the same, so it serializes like any other.
 *
 * What this does not reproduce from [ImageImportService] is the skipping of files that are already in
 * the location, so a second run over the same gallery writes the files again. [ImageImportTO.skipped]
 * is therefore always 0 here rather than answering a question this does not ask.
 */
@Service
class ScraperImageImportService(
    private val scraperService: ScraperService,
    private val browserFetcher: BrowserFetcher,
    private val locationService: LocationService,
    private val msetService: MsetService
) {

    /**
     * Imports the images of [url] into the location with [locationId] by running a scraper over the
     * rendered page.
     *
     * @param name the name of the mset and of the folder the files go into, the url when blank
     * @param pattern a regex an image url has to match, e.g. to take only the full size files of a page
     * that also links to its thumbnails. Blank takes every image the parser finds
     * @param scrollTimes how often the page is scrolled before its images are collected, since a
     * lazily loading gallery appends them while scrolling
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [BrowserFetcher.render]
     * @param persist false answers the draft without writing the media to the database. The files are
     * written either way, the way [ImageImportService.import] does it, so a draft costs the downloads
     * but not the rows
     * @throws NotAccessibleException when the location cannot receive files, the browser cannot be
     * started, [waitForSelector] does not appear, or the page holds no image at all
     * @throws org.endy.pmczero.exception.NotFoundException when no location has that id
     */
    fun import(
        locationId: Int,
        url: String,
        name: String? = null,
        pattern: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null,
        persist: Boolean = true
    ): ImageImportTO = importWith(
        locationId = locationId,
        url = url,
        scraper = scraperOf(pattern),
        name = name,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        persist = persist
    )

    /**
     * The same import as [import], with the scraper handed in rather than built here.
     *
     * The scraper is json, the same shape that is stored in `a.scanner.serialization` and that
     * [org.endy.pmczero.service.ScannerService] reads, so a scraper can be written once, stored, and
     * run from either place. Read with [ScanFormat], which is where the format of the project lives.
     *
     * Two things are worth knowing about a scraper that arrives this way rather than being built by
     * [scraperOf]:
     *
     * - it may download nothing. The scraper of [import] is what writes the files of an import, and a
     *   scraper without a [FileDownloader] in it records media whose files exist nowhere, which is the
     *   state this whole class exists to avoid. So one that does not download is refused rather than
     *   answered with a set of dangling media. [FileDownloader] and [MediaAdder] also both belong in it,
     *   or the media are recorded without being written.
     * - it may not write files at all, in which case [locationId] is only a name to file the set under.
     *   That is refused too, since the endpoint is an import.
     *
     * @param scraper the serialized scraper to run, i.e. the json of a [Scraper]
     * @throws NotAccessibleException when [scraper] is not the json of a scraper, when it holds no
     * worker that downloads the files of the media it records, when the location cannot receive files,
     * the browser cannot be started, [waitForSelector] does not appear, or the page holds no image
     * @throws org.endy.pmczero.exception.NotFoundException when no location has that id
     */
    fun importWith(
        locationId: Int,
        url: String,
        scraper: String,
        name: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null,
        persist: Boolean = true
    ): ImageImportTO = importWith(
        locationId = locationId,
        url = url,
        scraper = scraperOrFail(scrapeSerializable(scraper)),
        name = name,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        persist = persist
    )

    /**
     * The same import as [import], over a scraper that is already an object.
     *
     * The one place the import actually runs, so [import], [importWith] and a caller that holds a
     * scraper cannot drift apart in what they do with a page.
     */
    fun importWith(
        locationId: Int,
        url: String,
        scraper: Scraper,
        name: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null,
        persist: Boolean = true
    ): ImageImportTO {
        val location = writableLocation(locationId)
        val storageId = location.storageOrNull()?.id

        // rendered here rather than by the StructuredWorker below, which would fetch the page itself and
        // so lose the scroll count and the wait for a selector: a lazily loading gallery read without
        // scrolling answers only what was above the fold, and a single page application read at the load
        // event answers an empty shell
        val html = browserFetcher.render(url, waitForSelector, scrollTimes)

        val kontext = scraperService.getNewScanningContext(location, browserFetcher, folderFor(name, url))

        scraper.doWork(html, baseUriOf(url), kontext)

        // no image at all is reported rather than answered as an empty result, so a caller cannot mistake
        // a page whose images never loaded for one that holds none, and is spared an empty set
        if (kontext.mset?.media.isNullOrEmpty())
            throw NotAccessibleException("no images found on $url, so nothing to import")

        val media = kontext.mset!!.media

        // only when one was asked for: the [org.endy.pmczero.model.scraper.SetCreator] of the scraper has
        // already named the set after the page title, and overwriting that with the url would throw away
        // the one name that says what the images are of
        kontext.mset?.name = name?.takeIf { it.isNotBlank() } ?: kontext.mset?.name ?: url

        val saved = if (persist) msetService.save(kontext.mset!!) else null

        return ImageImportTO(
            locationId = locationId,
            url = url,
            storageId = storageId,
            msetId = saved?.id,
            found = media.size,
            imported = media.size,
            // this import does not skip anything, see the class comment
            skipped = 0,
            failed = kontext.failures.size,
            media = media.map { it.toTO() },
            failures = kontext.failures.map { ImageImportFailureTO(it.element, it.reason) }
        )
    }

    /**
     * The scraper this import runs: the page title as the name of the set, and every image the
     * [DomParser] finds recorded as a medium and downloaded.
     *
     * `abs:src` rather than `src`, which is what makes the urls absolute: jsoup resolves the attribute
     * against the base uri the caller passed in, and a page that writes `/bilder/1.jpg` has no absolute
     * url of its own to offer.
     *
     * [MediaAdder] and [FileDownloader] run as two scrapers over the same elements rather than as one
     * worker doing both, which is what the pipeline is for: they are separate steps that happen to need
     * the same element. Both ask the same [org.endy.pmczero.model.scraper.ScanPath] for the path, so the
     * medium and the file it points at cannot drift apart.
     *
     * [RecoveryWorker] wraps the one that writes, so an image that cannot be fetched is recorded as a
     * failure rather than ending the import and losing every image that would have worked. It wraps only
     * the download, since [MediaAdder] works on the url alone and has nothing to fail on.
     *
     * @param pattern a regex an image url has to match, `(.+)` for all of them. It goes into the parser
     * rather than being applied to a list of urls afterwards, which is what makes it a selector: with the
     * pattern in the parser the page is parsed once and the other branch sees the same elements
     */
    fun scraperOf(pattern: String?): Scraper {
        val imageRegex = pattern?.takeIf { it.isNotBlank() } ?: "(.+)"

        return Scraper(
            // the html itself is the element, since the page has already been fetched above
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser(imageRegex, "img[src]", "abs:src"), MediaAdder()),
                    Scraper(DomParser(imageRegex, "img[src]", "abs:src"), RecoveryWorker(FileDownloader()))
                )
            )
        )
    }

    /**
     * The images of the pages [url] links to, collected and nothing else: no file is downloaded, no
     * medium created, nothing stored.
     *
     * The same as [list], one page deeper: [list] collects the images of the page it is given, this
     * collects the images of every page that page links to. Which links are followed is [linkClass],
     * since a gallery index links every navigation entry on the site as well as the pages of the gallery.
     *
     * Two levels of [org.endy.pmczero.model.FoundElement.level] in one answer, which is what they are
     * for: level 1 is the index page and its links, level 2 the images found on the pages those links
     * lead to. So a caller can see both what was linked and what was found, and tell a missing image
     * from a missing page.
     *
     * Every link is followed to its end, so this costs a render per page the index links to. It is
     * still nothing like an import, which would fetch every single image on top of that.
     *
     * @param linkClass the css class of the links to follow, e.g. `gallery-link` on an index that
     * wraps each entry of the gallery in one. Blank follows every `a[href]` of the page, which on a
     * real site is every navigation link on it, so this is worth passing whenever the page offers a
     * class to pick the gallery by
     * @param pattern a regex an image url has to match on the pages that are followed. Blank takes
     * every image of them
     * @param scrollTimes how often a page is scrolled before its images are collected, since a lazily
     * loading gallery appends them while scrolling
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [BrowserFetcher.render]
     * @throws NotAccessibleException when the browser cannot be started or [waitForSelector] does not
     * appear
     */
    fun listLevel2(
        url: String,
        linkClass: String? = null,
        pattern: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null
    ): ImageListTO {
        val html = browserFetcher.render(url, waitForSelector, scrollTimes)

        val kontext = scraperService.getNewScanningContext(
            scraperService.catchupLocation(),
            browserFetcher,
            ""
        )

        level2ScraperOf(linkClass, pattern).doWork(html, baseUriOf(url), kontext)

        return ImageListTO(
            url = url,
            found = kontext.foundElements.size,
            elements = kontext.foundElements.map { FoundElementTO(it.level, it.element) }
        )
    }

    /**
     * The urls [url] offers as images, collected and nothing else: no file is downloaded, no medium
     * created, nothing stored.
     *
     * The call to make before an import, and cheaper than one by a factor of the number of images: a
     * render of the page and the parsing of its dom, where an import also fetches every single file. So
     * this is how a caller finds out whether a page holds what it was after, and which of its images are
     * worth having, before asking for any of it to be written.
     *
     * What it collects is up to [scraperOf] and to [pattern], so the answer is the same set of urls an
     * import would have taken, in the same order. That is the point of it sharing the parser: a list that
     * is not what the import would have fetched is not worth much as a preview.
     *
     * @param pattern a regex an image url has to match, `(.+)` for all of them. See [scraperOf]
     * @param scrollTimes how often the page is scrolled before its images are collected, since a lazily
     * loading gallery appends them while scrolling
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [BrowserFetcher.render]
     * @throws NotAccessibleException when the browser cannot be started or [waitForSelector] does not
     * appear
     */
    fun list(
        url: String,
        pattern: String? = null,
        scrollTimes: Int = 3,
        waitForSelector: String? = null
    ): ImageListTO {
        // rendered here rather than by the StructuredWorker, which would fetch the page itself and so lose
        // the scroll count and the selector wait
        val html = browserFetcher.render(url, waitForSelector, scrollTimes)

        // the catchup location rather than a real one: nothing is written, so there is nothing for a
        // location to be. Its empty uri would stop a scraper that did try to write, which is the answer
        // this wants rather than files in a folder nobody asked about.
        val kontext = scraperService.getNewScanningContext(
            scraperService.catchupLocation(),
            browserFetcher,
            ""
        )

        listScraperOf(pattern).doWork(html, baseUriOf(url), kontext)

        return ImageListTO(
            url = url,
            found = kontext.foundElements.size,
            elements = kontext.foundElements.map { FoundElementTO(it.level, it.element) }
        )
    }

    /**
     * The scraper [list] runs: every image of the page, collected and nothing else.
     *
     * No [SetCreator], no [MediaAdder] and no [FileDownloader], since [list] answers with the elements
     * rather than with a set of media, and each of those three would put something in the way of that.
     * A single scraper, so [org.endy.pmczero.model.scraper.FoundElementsWorker.foundElements] holds
     * exactly the images and nothing else, at level 1.
     *
     * The same [DomParser] as [scraperOf], deliberately: a preview that offered different urls than the
     * import would take is not a preview of that import.
     */
    fun listScraperOf(pattern: String?): Scraper = Scraper(
        PassThroughParser(),
        StructuredWorker(
            download = false,
            scrapers = listOf(
                Scraper(
                    DomParser(pattern?.takeIf { it.isNotBlank() } ?: "(.+)", "img[src]", "abs:src"),
                    FoundElementsWorker(1)
                )
            )
        )
    )

    /**
     * The [Scraper] that is [serialized], or a failure that says what was wrong with it.
     *
     * A [kotlinx.serialization.SerializationException] names the class or the field it stumbled on, which
     * is already the most useful thing a caller can be told about a broken scraper. It is not an
     * [Exception] this application maps, so it would reach a client as a bare 500; it is turned into a
     * [NotAccessibleException] to be answered as a 409 with its message, the way a browser that cannot be
     * started is.
     */
    private fun scrapeSerializable(serialized: String): Scraper =
        try {
            ScanFormat.json.decodeFromString<Scraper>(serialized)
        } catch (e: SerializationException) {
            throw NotAccessibleException(
                "the scraper is not a scraper this application knows: ${e.message ?: e.toString()}"
            )
        }

    /**
     * [scraper], checked for the one thing an import needs it to do, which is write the files of the media
     * it records.
     *
     * A scraper without a [FileDownloader] answers with media whose files exist nowhere: every url the
     * rest of the application builds for them points at a path that is not there, and nothing in the
     * answer says so. That is the state this class exists to produce, so a scraper arriving from a
     * caller is asked whether it really does it rather than being trusted.
     *
     * The search is over the whole worker tree rather than the top of it, since the download sits
     * somewhere below a [StructuredWorker] in every scraper worth running, and often inside a
     * [RecoveryWorker] that wraps it.
     */
    private fun scraperOrFail(scraper: Scraper): Scraper {
        if (!scraper.worker.writesFiles())
            throw NotAccessibleException(
                "the scraper holds no FileDownloader, so the media it records would point at files " +
                    "that are never written"
            )

        return scraper
    }

    /** whether [worker], or anything below it, writes the file of the element it is handed */
    private fun Worker.writesFiles(): Boolean = when (this) {
        is FileDownloader -> true
        is RecoveryWorker -> worker.writesFiles()
        is StructuredWorker -> scrapers.any { it.worker.writesFiles() }
        else -> false
    }

    /**
     * The location the files are written to, checked for the one thing this import needs it to do.
     *
     * A http location is refused rather than silently ignored: it has no file system path, so there is
     * nowhere to put the bytes, and the alternative would be recording media whose files exist nowhere.
     */
    private fun writableLocation(locationId: Int): Location {
        val location = locationService.findById(locationId)

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
     * anything at all, and the folder is derived from one. One folder per import, so a second gallery
     * cannot overwrite the files of the first one.
     */
    private fun folderFor(name: String?, url: String): String {
        val raw = name?.takeIf { it.isNotBlank() } ?: url
        return sanitise(raw).ifBlank { "import" }
    }

    /** [text] reduced to what a single file name may hold: no separators, no reserved characters */
    private fun sanitise(text: String): String =
        text.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('.', ' ')

    /** the part of [url] a relative image url is resolved against, i.e. everything up to the last slash */
    private fun baseUriOf(url: String): String = url.substringBeforeLast('/') + "/"
}
