package org.endy.pmczero.ressource

import com.microsoft.playwright.options.WaitForSelectorState
import org.endy.pmczero.exception.BadRequestException
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.service.BrowserFetcher
import org.endy.pmczero.service.ImageImportService
import org.endy.pmczero.service.ScraperImageImportService
import org.endy.pmczero.service.ScraperService
import org.endy.pmczero.to.ImageImportTO
import org.endy.pmczero.to.ImageListTO
import org.endy.pmczero.to.MsetTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Getting images out of a web page and into the database.
 *
 * Three calls, in the order one would use them:
 *
 * 1. [render] to see whether the browser reaches the page at all and what its dom looks like once the
 *    javascript has run. It answers html rather than media, so it is the one to hit first when
 *    nothing comes out of [importImages].
 * 2. [importImages] to fetch the images into a location and record them as media. This is the call
 *    that writes.
 * 3. [importImagesWithScraper] for the same import with the page picked apart by the scraper pipeline,
 *    which is what to reach for when the images are not quite what [importImages] collects.
 * 4. [importWithSerializedScraper] for the same import with the scraper itself handed in as json, for a
 *    scraper that is not just the images of the page.
 * 5. [listScraperImages] to see which image urls a page offers, downloading nothing. It costs a render
 *    where an import costs a render per image, so it is the one to hit before committing to an import.
 * 6. [listScraperImagesLevel2] for the same, over the pages a gallery index links to rather than over
 *    the index page itself.
 * 7. [draft] to see what a scraper makes of a page without storing anything, which is worth a look
 *    when a page needs its elements picked differently.
 *
 * All of them are POSTs that write or that cost a browser and a wait. They are not GETs because a
 * crawler, a link preview in a mail client or a browser prefetch would fire them off by following a
 * link, and this project sits on a local network with no authentication in front of it.
 */
@RestController
@RequestMapping("/api/scrape")
class ScraperRessource(
    private val scraperService: ScraperService,
    private val browserFetcher: BrowserFetcher,
    private val imageImportService: ImageImportService,
    private val scraperImageImportService: ScraperImageImportService
) {

    /**
     * The dom of [url] as a browser ends up with it, as html.
     *
     * The plain http response is not what this answers, since that is the whole point of it: it is
     * what the page looks like after its scripts have filled it. Diffing it against
     * [Downloader.getAsString][org.endy.pmczero.service.Downloader.getAsString] tells a caller
     * whether a gallery is one of the javascript kind.
     *
     * ### Single page applications
     *
     * The load event is not enough here. An angular, react or vue app serves a shell and fills it in
     * afterwards, so without a [waitForSelector] the answer is the shell: the empty root element and
     * whatever the application got done with in between. So for `http://localhost:4200/locations`:
     *
     *     waitForSelector=app-locations          the component of that route, once it is instantiated
     *     waitForSelector=.location-row          a row of the list, once the data has arrived
     *     waitForSelector=[data-loaded]          a marker attribute the app sets itself
     *
     * The first is the one to start with, since it only asks for the route to be built and does not
     * depend on how the list is marked up. The second is the one that actually proves the data is
     * there. Both fail with a 409 rather than a silently half rendered dom if they never appear.
     *
     * ### Visible or merely there
     *
     * A wait is only satisfied when the element reaches a state, and the default of that state is the
     * one that surprises people. Without a [waitForSelectorState] the wait is for the element to be
     * *visible*, which in the browser means it has a non empty bounding box and is not hidden. That is
     * the right question to ask of a row of a list and the wrong one to ask of the container the rows
     * go into: an empty wrapper has no box at all, so
     *
     *     waitForSelector=[data-testid=gallery-items-container]
     *
     * sits in the dom from the moment the application is built and is never visible until something
     * is in it. On a lazy loading gallery that times out with
     *
     *     the page did not get ready within 45000ms: - waiting for locator(...) to be visible
     *
     * which reads like a wrong selector and is not one. Ask for the weaker state and let the scroll
     * do the rest:
     *
     *     waitForSelector=[data-testid=gallery-items-container]&waitForSelectorState=attached&scrollTimes=5
     *
     * [waitForSelectorState] takes the four states playwright knows, in any case: `attached`,
     * `detached`, `hidden`, `visible`. `attached` is the one for a container that is filled
     * afterwards. `hidden` is the one for a spinner that has to go away, which is how a page that
     * shows no marker of its own is waited for. Blank is `visible`, which is what every call did
     * before there was a state to pick. Anything else answers 400 rather than being ignored, since a
     * state that is quietly dropped looks exactly like the timeout it was meant to prevent.
     *
     * Note that a hash looking css class is not a good selector here, whatever state it is waited
     * for: one built by css modules or a styled component changes with every deploy of the site, so
     * a selector naming it works until it does not. `data-testid` is there to stay.
     *
     * Answers 409 when the browser is disabled or cannot be started, see the `pmc.browser.*`
     * properties and `gradlew installPlaywrightBrowsers`.
     *
     * @param waitForSelector a css selector to wait for, i.e. an element the application renders
     * only once it is done. Leave it out for a server rendered page, where the load event is enough
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See above
     * @param scrollTimes how often to scroll to the bottom before answering. A lazily loading list
     * only holds its rows below the fold, so this is what gets all of them rather than the first
     * screenful. It stops on its own once a scroll changes nothing, so this can be set generously
     */
    @PostMapping("/render")
    fun render(
        @RequestParam url: String,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "0") scrollTimes: Int
    ): String = browserFetcher.render(url, waitForSelector, scrollTimes, stateOf(waitForSelectorState))

    /**
     * Imports the images of [url] into the location with [locationId], answering what happened to
     * each of them.
     *
     * The files are written into the location and, unless [persist] is false, recorded as an mset of
     * media. A single image that cannot be fetched does not fail the call, see [ImageImportTO] for
     * the three buckets the outcome is split into.
     *
     * The location has to be a MAIN_FS one, since there has to be a directory to write into;
     * anything else answers 409 rather than recording media whose files are nowhere.
     *
     * Answers 404 when the location does not exist.
     *
     * @param name the name of the mset and of the folder the files go into, the url when blank
     * @param pattern a regex an image url has to match, e.g. to take only the full size files of a
     * page that also links to its thumbnails. Blank takes every image
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [render]
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See [render]
     * @param persist false answers the draft without writing it
     * @param skipExisting leave files alone that are in the location already, so a second run over
     * the same gallery costs one query instead of another few hundred downloads
     */
    @PostMapping("/images")
    fun importImages(
        @RequestParam url: String,
        @RequestParam locationId: Int,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) pattern: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int,
        @RequestParam(defaultValue = "true") persist: Boolean,
        @RequestParam(defaultValue = "true") skipExisting: Boolean
    ): ImageImportTO = imageImportService.import(
        locationId = locationId,
        url = url,
        name = name,
        pattern = pattern,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = stateOf(waitForSelectorState),
        persist = persist,
        skipExisting = skipExisting
    )

    /**
     * The same import as [importImages], with the page picked apart by the scraper pipeline rather than
     * by [ImageImportService], and answered in the same [ImageImportTO].
     *
     * Two imports of the same thing, so it is worth saying which to call:
     *
     * - this one, when what the page offers has to be expressed as a parser: the links of a page
     *   rather than its images, a `data-` attribute, a css selector. The scraper of this call is built
     *   in [ScraperImageImportService.scraperOf] and could as well come out of the database, which is
     *   what [org.endy.pmczero.service.ScannerService] is set up for.
     * - [importImages], when the images are wanted and nothing about how they are picked matters. Its
     *   handling of what is already stored is the more careful of the two: `skipExisting` there leaves
     *   the files of an earlier import alone, while this endpoint has no such parameter and writes them
     *   again. So a second run over the same gallery is worth through that one and not through this.
     *
     * @param name the name of the mset and of the folder the files go into, the url when blank
     * @param pattern a regex an image url has to match. Blank takes every image of the page
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [render]
     * @param persist false answers the draft without writing the media to the database. The files are
     * written either way
     */
    /**
     * The same import as [importImagesWithScraper], with the scraper handed in as json rather than built
     * from a pattern.
     *
     * This is the one to call for a scraper that is not just "the images of the page". What it picks is
     * whatever the json says: a [DomParser] with any tag and any attribute, a [DomParser] with a regex of
     * its own in place of the pattern, any nesting of them. That is the difference from the endpoint
     * before, whose pattern is a single url filter over `img[src]` and nothing else.
     *
     * The json is the shape that is stored in `a.scanner.serialization`, so a scraper that has been
     * stored, or written by a caller that once fetched it from
     * [org.endy.pmczero.service.ScannerService.getScanner], can be run from here without being rewritten.
     *
     * The scraper has to download its own files: one that only records media is refused with a 409 rather
     * than answered with a set whose every url points at a file that was never written, see
     * [ScraperImageImportService.importWith].
     *
     * @param scraper the serialized [org.endy.pmczero.model.scraper.Scraper] to run
     * @param name the name of the mset and of the folder the files go into. Blank names the set after
     * the page title the scraper picks up
     * @param waitForSelector a css selector to wait for before collecting. See [render]
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See [render]
     * @param persist false answers the draft without writing the media to the database. The files are
     * written either way
     */
    @PostMapping("/scraper-import")
    fun importWithSerializedScraper(
        @RequestParam url: String,
        @RequestParam locationId: Int,
        @RequestParam scraper: String,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int,
        @RequestParam(defaultValue = "true") persist: Boolean
    ): ImageImportTO = scraperImageImportService.importWith(
        locationId = locationId,
        url = url,
        scraper = scraper,
        name = name,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = stateOf(waitForSelectorState),
        persist = persist
    )

    /**
     * The image urls of [url], collected and nothing else: no file is downloaded, no medium created,
     * nothing stored.
     *
     * The call to make before an import, since it costs a render of the page where an import also fetches
     * every single image. So it answers whether a page holds what one was after, and which of its images
     * are worth having, before anything is written.
     *
     * The same parser as `/scraper-images`, deliberately: a list of urls that is not what the import
     * would have fetched is not worth much as a preview of it.
     *
     * No location is needed, since nothing is written anywhere.
     *
     * @param pattern a regex an image url has to match, e.g. to list only the full size files of a page
     * that also links to its thumbnails. Blank takes every image of the page
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [render]
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See [render]
     * @param scrollTimes how often the page is scrolled before its images are collected, since a
     * lazily loading gallery appends them while scrolling
     */
    /**
     * The images of the pages [url] links to, collected and nothing else: no file is downloaded, no
     * medium created, nothing stored.
     *
     * The same as [listScraperImages], one page deeper, for a gallery whose images are not on its index
     * page but on the pages that index links to.
     *
     * The answer holds two [org.endy.pmczero.to.FoundElementTO.level]s, which is what tells a caller
     * apart a gallery that has no images from a gallery whose pages could not be reached: level 1 is
     * the links found on the index page, level 2 the images found on the pages they lead to. A link at
     * level 1 with nothing at level 2 under it is a page that answered and held no images, unless it
     * is listed in [org.endy.pmczero.to.ImageListTO.failures], which is where a page that could not be
     * read at all ends up.
     *
     * Every matched link is followed to its end, so the call costs a render per linked page. Pass
     * [linkClass] whenever the page offers one: on a real site `a[href]` alone follows every navigation
     * link on it.
     *
     * @param linkClass the css class of the links to follow, e.g. `gallery-link`. Blank follows every
     * link of the page
     * @param pattern a regex an image url has to match on the pages that are followed. Blank takes
     * every image of them
     * @param waitForSelector a css selector to wait for before collecting, needed on a single page
     * application whose images do not exist at the load event. See [render]
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See [render]
     * @param scrollTimes how often the page is scrolled before its images are collected, since a
     * lazily loading gallery appends them while scrolling
     */
    @PostMapping("/scraper-image-list-level2")
    fun listScraperImagesLevel2(
        @RequestParam url: String,
        @RequestParam(required = false) linkClass: String?,
        @RequestParam(required = false) pattern: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int
    ): ImageListTO = scraperImageImportService.listLevel2(
        url = url,
        linkClass = linkClass,
        pattern = pattern,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = stateOf(waitForSelectorState)
    )

    @PostMapping("/scraper-image-list")
    fun listScraperImages(
        @RequestParam url: String,
        @RequestParam(required = false) pattern: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int
    ): ImageListTO = scraperImageImportService.list(
        url = url,
        pattern = pattern,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = stateOf(waitForSelectorState)
    )

    @PostMapping("/scraper-images")
    fun importImagesWithScraper(
        @RequestParam url: String,
        @RequestParam locationId: Int,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) pattern: String?,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "3") scrollTimes: Int,
        @RequestParam(defaultValue = "true") persist: Boolean
    ): ImageImportTO = scraperImageImportService.import(
        locationId = locationId,
        url = url,
        name = name,
        pattern = pattern,
        scrollTimes = scrollTimes,
        waitForSelector = waitForSelector,
        waitForSelectorState = stateOf(waitForSelectorState),
        persist = persist
    )

    /**
     * The mset the standard image scraper builds from [url], with the media it found and nothing
     * stored.
     *
     * The counterpart of [importImages] that stores nothing: the same rendered page, picked apart by
     * the same scraper, but answering a draft. Useful to see which elements a page offers before
     * committing to a download, and to check that the dom really holds the images at all.
     *
     * @param browser false reads the page over plain http, which answers a different set of elements
     * for a page that builds its dom in javascript. True by default, since that is the case the
     * browser was added for
     * @param waitForSelector a css selector to wait for, ignored when [browser] is false. See
     * [render]. A draft without one is the one call of this ressource that answers the shell of a
     * single page application rather than the page, so on an application that fills itself in this
     * is the parameter that makes the draft worth reading
     * @param waitForSelectorState what `waitForSelector` has to reach to count as done. Blank waits
     * for it to be visible. See [render]
     */
    @PostMapping("/draft")
    fun draft(
        @RequestParam url: String,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(required = false) waitForSelectorState: String?,
        @RequestParam(defaultValue = "true") browser: Boolean,
        @RequestParam(defaultValue = "3") scrollTimes: Int
    ): MsetTO {
        // a placeholder location, so nothing of the draft points at a real storage: this is a look at
        // what a page holds, not a set that is about to be saved
        val kontext = if (browser)
            scraperService.getNewScanningContext(scraperService.catchupLocation(), browserFetcher)
        else
            scraperService.getNewScanningContext(scraperService.catchupLocation())

        val scraper = imageScraper()

        // with the browser the page is rendered once here and the workers parse that html, rather
        // than every worker fetching the page again: a gallery that appends images as it is scrolled
        // would otherwise be read several times, each time holding something different
        if (browser) scraper.doWork(
            browserFetcher.render(url, waitForSelector, scrollTimes, stateOf(waitForSelectorState)),
            baseUriOf(url),
            kontext
        )
        else scraper.doWork(url, "", kontext)

        // on the set the scraper built rather than the one the kontext started with, since the
        // [SetCreator] of a scraper replaces it. Only the url is recorded: this draft runs against the
        // placeholder location of catchupLocation(), which has no id, so there is nothing to record for
        // one and no real location behind it to record
        kontext.mset?.url = url

        return kontext.mset?.toTOwithMedia() ?: MsetTO()
    }

    /**
     * The scraper the draft is built with: the page title as the set name, every image of the page as
     * a medium.
     *
     * Built here rather than stored, because there is nowhere to store one yet: the scrapers are meant
     * to come out of the database as configuration, which is what [ScraperService] is set up for but
     * has not done. So the two are put together in code and are meant to be moved once a scraper
     * really is something a user configures.
     *
     * `download = false`, because the page has already been fetched by the caller of this scraper:
     * the workers parse the html they are handed rather than going to the url again.
     *
     * `abs:src` rather than `src`, which is what makes the urls absolute: jsoup resolves the
     * attribute against the base uri the caller passed in, and a page that writes `/bilder/1.jpg` has
     * no absolute url of its own to offer.
     */
    private fun imageScraper(): Scraper = Scraper(
        PassThroughParser(),
        StructuredWorker(
            download = false,
            scrapers = listOf(
                Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder())
            )
        )
    )

    /** the part of [url] a relative image url is resolved against, i.e. everything up to the last slash */
    private fun baseUriOf(url: String): String = url.substringBeforeLast('/') + "/"

    /**
     * The [WaitForSelectorState] a `waitForSelectorState` parameter names, null when it names none.
     *
     * Taken as a string rather than bound to the enum by spring, because spring's converter answers
     * an unknown name with null rather than with an error, and on a nullable parameter that null is
     * indistinguishable from a parameter that was not sent. A `waitForSelectorState=attaced` would
     * then be dropped and the call would go on to wait for the default, i.e. to fail with the very
     * timeout the parameter was meant to prevent, with nothing in the answer to say why.
     *
     * Blank is null rather than `visible` spelled out, so that a caller who says nothing keeps
     * exactly the behaviour of a caller who says nothing at all: the state is left unset on the
     * playwright options and the default of the library applies, which is what
     * [BrowserFetcher.waitFor] documents.
     *
     * Case is not significant, since a url query string is written by hand far more often than it is
     * generated, and the states are lower case in the documentation of playwright itself.
     *
     * @throws BadRequestException when [value] is not one of the four states
     */
    private fun stateOf(value: String?): WaitForSelectorState? =
        value?.takeIf { it.isNotBlank() }?.let { raw ->
            WaitForSelectorState.values().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: throw BadRequestException(
                    "waitForSelectorState=$raw is not a wait state, expected one of " +
                        WaitForSelectorState.values().joinToString(", ") { it.name.lowercase() }
                )
        }
}
