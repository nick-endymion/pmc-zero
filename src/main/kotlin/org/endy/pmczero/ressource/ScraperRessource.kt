package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.service.BrowserFetcher
import org.endy.pmczero.service.ImageImportService
import org.endy.pmczero.service.ScraperService
import org.endy.pmczero.to.ImageImportTO
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
 * 3. [draft] to see what a scraper makes of a page without storing anything, which is worth a look
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
    private val imageImportService: ImageImportService
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
     * Answers 409 when the browser is disabled or cannot be started, see the `pmc.browser.*`
     * properties and `gradlew installPlaywrightBrowsers`.
     *
     * @param waitForSelector a css selector to wait for, i.e. an element the application renders
     * only once it is done. Leave it out for a server rendered page, where the load event is enough
     * @param scrollTimes how often to scroll to the bottom before answering. A lazily loading list
     * only holds its rows below the fold, so this is what gets all of them rather than the first
     * screenful. It stops on its own once a scroll changes nothing, so this can be set generously
     */
    @PostMapping("/render")
    fun render(
        @RequestParam url: String,
        @RequestParam(required = false) waitForSelector: String?,
        @RequestParam(defaultValue = "0") scrollTimes: Int
    ): String = browserFetcher.render(url, waitForSelector, scrollTimes)

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
     * application whose images do not exist at the load event. See [render] for how to pick one
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
        persist = persist,
        skipExisting = skipExisting
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
     */
    @PostMapping("/draft")
    fun draft(
        @RequestParam url: String,
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
        if (browser) scraper.doWork(browserFetcher.render(url, scrollTimes = scrollTimes), baseUriOf(url), kontext)
        else scraper.doWork(url, "", kontext)

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
}
