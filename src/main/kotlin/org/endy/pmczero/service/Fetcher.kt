package org.endy.pmczero.service

import java.io.File

/**
 * Where the content of a remote ressource comes from.
 *
 * Two implementations, and the scraper pipeline in `org.endy.pmczero.model.scraper` is written
 * against this rather than against one of them:
 *
 * - [Downloader] asks the server over plain http. Cheap, no browser, no session, and enough for
 *   every page that sends its images in the html.
 * - [BrowserFetcher] drives a real browser, which is the only way to get a page that builds its dom
 *   in javascript, and the only way to use the cookies of a browser a user is already logged in
 *   with.
 *
 * So the same [org.endy.pmczero.model.scraper.Scraper] runs over both a static and a rendered page,
 * and which one it is stays a property of the scanning rather than of the pipeline.
 */
interface Fetcher {

    /**
     * The content of [urlString] as text, which for a page is its html.
     *
     * @param withProxy whether to go through the proxy. Honoured by [Downloader], which has one per
     * call; [BrowserFetcher] ignores it and uses the browser it was configured with, because a
     * proxy is a property of a browser rather than of a single request.
     * @param waitForLoadEvent whether a page is waited for until everything it names has come back,
     * which is what the load event is. False answers as soon as the html is parsed and the deferred
     * scripts have run, which is the whole question for a caller that is after the urls on a page
     * rather than after its pictures: the load event counts the images of a gallery, so a page of
     * two hundred of them holds the answer up until all two hundred are in, and one unreachable
     * picture holds it up for the whole navigation timeout. See [BrowserFetcher.render]
     *
     * True, so that a caller saying nothing about it gets what every call got before there was
     * anything to say. Only a browser has a load event to wait for at all: [Downloader] asks a server
     * for a document and there is nothing beyond the response, so it answers as soon as it has that.
     */
    fun getAsString(
        urlString: String,
        withProxy: Boolean = true,
        waitForLoadEvent: Boolean = true
    ): String

    /**
     * Downloads [urlString] into [directory], naming the file after the last segment of the url.
     *
     * Kept for the callers that only want "the file next to the others"; [downloadTo] is what the
     * scraper uses, since it has to place the file itself.
     */
    fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean = false)

    /**
     * Downloads [urlString] to exactly [target], overwriting it, and answers the file it wrote.
     *
     * Exact rather than derived from the url, because the caller has already decided what the file
     * is called: two images of one gallery often share a file name, and a url may carry none at all.
     *
     * Whatever writes [target] has to make it atomic, i.e. finish in a file next to it and move that
     * into place, so a download that dies half way cannot leave a truncated image behind under a
     * name the database is about to point at.
     *
     * The directory [target] lives in is created when it is not there yet, so a caller may hand over
     * a path whose folder does not exist yet. Both implementations do that, so a caller that places
     * files into a fresh folder does not have to create it first for either of them.
     *
     * @throws org.endy.pmczero.exception.NotAccessibleException when the ressource cannot be
     * fetched or [target] cannot be written
     */
    fun downloadTo(urlString: String, target: File, withProxy: Boolean = false): File
}
