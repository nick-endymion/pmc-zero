package org.endy.pmczero.service

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.Proxy
import com.microsoft.playwright.options.RequestOptions
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState
import org.endy.pmczero.exception.NotAccessibleException
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Fetches pages and images through a real browser, which is what [Downloader] cannot do.
 *
 * Plain http only ever sees what a server sends to a client that asks for the html and nothing
 * more. A gallery that fills itself in with javascript, that loads its next page when the window is
 * scrolled to the bottom, or that hands out its images behind a login, sends none of its images in
 * that html. Driving a browser fixes all three at once, because the browser runs the same javascript
 * a user would and then hands over the dom it ended up with.
 *
 * ### Which browser
 *
 * Playwright ships chromium, firefox and webkit and no opera. Three ways to get a browser, in the
 * order they are tried, all of them configured rather than decided in code:
 *
 * 1. `pmc.browser.cdp-endpoint` set: attach to a browser that is already running, e.g. an opera
 *    started with `--remote-debugging-port=9222`. This is the only way to scrape a page that needs a
 *    login, because the cookies that browser already has are the ones the scrape runs with. Nothing
 *    of that browser is closed afterwards, since it is not ours.
 * 2. `pmc.browser.executable-path` set: start that binary ourselves. Works for opera and every other
 *    chromium based browser, because playwright drives chromium over the devtools protocol. Without
 *    the debug port though, so this gets a fresh profile and no login.
 * 3. Neither set: start the chromium that `gradlew installPlaywrightBrowsers` downloaded. This is
 *    the default because it is the browser playwright is tested against, so it is the one that
 *    behaves.
 *
 * ### Cost and threading
 *
 * Starting a browser and its driver costs a second, so both are kept until the application shuts
 * down, and [destroy] closes them. The api is synchronous and blocking, so a scrape of a big gallery
 * occupies its thread for as long as it runs: that is fine in the worker thread a scrape is meant to
 * be run from, and is the reason [withPage] serializes access instead of trying to share a page
 * between two scrapes.
 */
@Service
class BrowserFetcher @Autowired constructor(
    @Value("\${pmc.browser.enabled:true}") private val enabled: Boolean,
    @Value("\${pmc.browser.headless:true}") private val headless: Boolean,
    @Value("\${pmc.browser.cdp-endpoint:}") private val cdpEndpoint: String,
    @Value("\${pmc.browser.executable-path:}") private val executablePath: String,
    @Value("\${pmc.browser.proxy-socks:}") private val proxySocks: String,
    @Value("\${pmc.browser.navigation-timeout-ms:45000}") private val navigationTimeoutMs: Int
) : Fetcher, DisposableBean {

    /**
     * the driver, started on first use. Null until something needs a browser, so an application that
     * never scrapes never pays for one
     */
    private var playwright: Playwright? = null

    /** the browser, and whether it is one we attached to rather than started ourselves */
    private var browser: Pair<Browser, Boolean>? = null

    /**
     * The failure of the last attempt to start a browser, kept so [withBrowser] can report it.
     *
     * A field rather than a return value, because that is what [startBrowser] can express: it either
     * has a browser or it has this, and both are on the way out to the caller anyway.
     */
    private var startupFailure: PlaywrightException? = null

    /**
     * guards [playwright], [browser] and the pages they hand out.
     *
     * Playwright's java objects are single threaded by design, so everything below runs inside this
     * lock. That serializes concurrent scrapes rather than letting them corrupt each other, which is
     * the lesser evil: a second browser per scrape would cost a second of startup and its own
     * several hundred megabytes for no gain.
     */
    private val lock = Any()

    /**
     * The configured timeout as the playwright api wants it, a double of milliseconds.
     *
     * The api is generated and takes every timeout as a double, so a value read from a property as an
     * Int has to be widened here rather than at each of the three call sites.
     */
    private val timeoutMs: Double get() = navigationTimeoutMs.toDouble()

    /**
     * The dom of [urlString] as the browser ended up with it, so the javascript of the page has run.
     *
     * A drop-in replacement for [Downloader.getAsString] that a scraper cannot tell apart from it,
     * which is what lets the same scraper run over a rendered and an unrendered page.
     *
     * The load event is not enough on a single page application. Angular, react and vue all hand out
     * an html shell and fill it in afterwards, so the dom at the load event holds the empty root
     * element and nothing else; what this answers then is the shell, not the page. A [waitForSelector]
     * is what closes that gap: it names an element the application only puts on the page once its
     * data has arrived, and reading the dom after that is what the page looks like in a browser tab.
     *
     * @param waitForSelector a css selector to wait for before reading the dom, i.e. an element the
     * application renders only when it is done. Null waits for the load event alone, which is the
     * right choice for a server rendered page and the wrong one for an application that fills itself
     * in afterwards. A selector that never appears fails the call with the timeout rather than
     * answering a half rendered dom as if it were complete
     * @param waitForSelectorState what "appears" has to mean for [waitForSelector] to be satisfied.
     * Null leaves the choice to playwright, which waits for the element to be visible. That is the
     * right question for a row of a list and the wrong one for the container the rows go into: an
     * empty wrapper has no box, so it sits in the dom forever and never becomes visible. Ask for
     * [WaitForSelectorState.ATTACHED] on a page that puts an empty container on the page first and
     * fills it afterwards, which is what a lazy loading gallery does. See [waitFor]
     * @param scrollTimes how often to scroll to the bottom before reading the dom. Every number
     * above zero is what a lazy loading list needs, since it appends more rows as it is approached;
     * 0 leaves the page where it landed. Stops on its own as soon as a scroll changes nothing, so a
     * generous number does not cost a generous number of waits
     * @throws NotAccessibleException when the browser is disabled, cannot be started, [waitForSelector]
     * does not appear in time, or the page cannot be read
     */
    fun render(
        urlString: String,
        waitForSelector: String? = null,
        scrollTimes: Int = 0,
        waitForSelectorState: WaitForSelectorState? = null
    ): String = withPage { page ->
            page.navigate(urlString, navigateOptions())
            waitForSelector?.let {
                waitFor(page, it, waitForSelectorState)
            }
            scroll(page, scrollTimes)
            page.content()
        }

    /**
     * The urls of the images of [urlString], as the rendered dom holds them.
     *
     * Read out of the dom the browser built rather than out of the html the server sent, which is the
     * whole point of using it: an image that a script injected is in here, one that a script swapped
     * in after a load event is in here in its final form, and `currentSrc` reports the file the
     * browser really fetched when a page offers a `srcset` of several sizes.
     *
     * The lazy loading attributes are in the fallback chain as well, for the galleries that only put
     * the real url in `data-src` and keep `src` on a placeholder, since those images never enter
     * `currentSrc` at all.
     *
     * Absolute, because the browser resolves the attribute against the page before this sees it.
     * Distinct and stripped of blanks, since a gallery repeats its images in a header, a footer and
     * a lightbox.
     *
     * Takes a [waitForSelector] for the same reason [render] does: on a single page application the
     * images do not exist at the load event, so collecting them without one collects the shell. And a
     * [waitForSelectorState] for the same reason [render] takes one, since the gallery container
     * these images go into is empty and boxless until they arrive.
     *
     * @param scrollTimes how often to scroll before collecting, since lazy loading appends images
     * while scrolling rather than all at once
     * @param waitForSelectorState what "appears" has to mean for [waitForSelector] to be satisfied.
     * See [render]
     */
    fun imageUrls(
        urlString: String,
        scrollTimes: Int = 3,
        waitForSelector: String? = null,
        waitForSelectorState: WaitForSelectorState? = null
    ): List<String> = withPage { page ->
        page.navigate(urlString, navigateOptions())
        // before the scrolls, so the scroll also waits for a container that an application only
        // builds once its data is there: scrolling an empty list moves nothing and would stop the
        // scroll loop after one round
        waitForSelector?.let {
            waitFor(page, it, waitForSelectorState)
        }
        scroll(page, scrollTimes)
        // evaluated in the page rather than through a locator per image, so one round trip collects
        // all of them instead of one per image
        val collected = page.evaluate(IMAGE_URL_SCRIPT)
        (collected as? List<*>)
            ?.filterIsInstance<String>()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()
    }

    override fun getAsString(urlString: String, withProxy: Boolean): String =
        render(urlString)

    override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {
        downloadTo(urlString, File(directory, fileNameOf(urlString)))
    }

    /**
     * Downloads [urlString] to exactly [target] with the cookies of the browser session.
     *
     * The bytes come from the page's own request context rather than from a second connection, which
     * is what makes this work behind a login: `page.request()` carries the cookies, the user agent
     * and the referrer of the session, so an image that only loads for a logged in user loads here
     * too.
     *
     * Written through a temporary file next to the target and moved into place, so a download that
     * fails half way leaves no truncated image behind under a name the database is about to point
     * at. The move rather than a rename because renameTo refuses to replace an existing file on
     * windows, which a second run over the same gallery does hit.
     *
     * @throws NotAccessibleException when the ressource answers an error, returns nothing, or
     * [target] cannot be written
     */
    override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File =
        withPage { page ->
            val response = page.request()
                .get(urlString, RequestOptions.create().setTimeout(timeoutMs))
            if (!response.ok())
                throw NotAccessibleException(
                    "${urlString} answered ${response.status()} ${response.statusText()}"
                )

            val body = response.body()
                ?: throw NotAccessibleException("${urlString} returned no content")

            createParentDirectory(target)
            val directory = target.parentFile?.toPath() ?: target.absoluteFile.parentFile.toPath()
            // a prefix of at least three characters is required, which a two letter file name plus the
            // dot would not be
            val temp = Files.createTempFile(directory, "${target.name}.", ".part").toFile()

            try {
                Files.write(temp.toPath(), body)
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                throw NotAccessibleException("could not write ${urlString} to ${target.path}: ${e.message}")
            } finally {
                // a no-op on the success path, where the file has been moved away, and the cleanup
                // that keeps a failed download from leaving a stray .part in the location
                temp.delete()
            }

            target
        }

    /** Closes the browser and the driver, if either was ever started. */
    override fun destroy() = synchronized(lock) {
        // an attached browser is the user's, and closing it here would shut the window they are
        // working in, so only the connection to it is dropped
        browser?.takeIf { !it.second }?.first?.close()
        browser = null
        playwright?.close()
        playwright = null
    }
    /**
     * Runs [block] against a page, creating one per call when the browser is ours and reusing the
     * page of an attached one.
     *
     * A context per call in the first case, so the cookies and the storage of one scrape cannot leak
     * into the next: a gallery behind a login would otherwise log the next scrape in as the user
     * without anybody asking. In the second case the default context of the attached browser is used
     * and its page is left open, since closing it would close a tab the user can see.
     *
     * A [PlaywrightException] is translated rather than passed on, because its message is about the
     * browser setup ("executable doesn't exist") far more often than about the page, and an unhandled
     * one would answer a plain 500 for what is a missing `gradlew installPlaywrightBrowsers`.
     */
    private fun <T> withPage(block: (Page) -> T): T = withBrowser { browser, attached ->
        if (attached) {
            val context = browser.contexts().firstOrNull() ?: browser.newContext()
            val existing = context.pages().firstOrNull()
            if (existing != null) block(existing)
            // a tab this code opened in a browser the user is looking at is closed again; one that was
            // already there is theirs, and closing it would take a window away from them
            else context.newPage().use { page -> block(page) }
        } else {
            browser.newContext().use { context ->
                context.newPage().use { page -> block(page) }
            }
        }
    }

    /**
     * The browser to work with, starting playwright and the browser on first use.
     *
     * @param block what to do with the browser and whether it is one we attached to
     */
    private fun <T> withBrowser(block: (Browser, Boolean) -> T): T = synchronized(lock) {
        if (!enabled)
            throw NotAccessibleException(
                "the browser is disabled, set pmc.browser.enabled to scrape with one"
            )

        val (running, attached) = browser ?: start()

        try {
            block(running, attached)
        } catch (e: PlaywrightException) {
            // a browser that died mid scrape stays dead in the cache, so the next call would fail the
            // same way forever; dropping it lets the next call start a fresh one. Closed rather than
            // just forgotten, so the driver process does not linger either
            if (isBrowserGone(e)) {
                closeQuietly()
                browser = null
                playwright = null
            }
            throw NotAccessibleException(reasonOf(e))
        }
    }

    /**
     * Closes the browser and the driver, ignoring a failure of either.
     *
     * For the paths where something has already gone wrong, so a second failure while cleaning up
     * must not replace the message that explains the first one.
     */
    private fun closeQuietly() {
        try {
            browser?.first?.close()
            playwright?.close()
        } catch (e: PlaywrightException) {
            // already gone, which is what this is for
        }
    }

    /** Starts playwright and the browser, or null when that cannot be done. */
    private fun start(): Pair<Browser, Boolean> {
        val started = startBrowser() ?: throw NotAccessibleException(reasonOf(null))

        // only cached once it exists, so a failed start leaves nothing behind to retry with
        browser = started
        return started
    }

    /**
     * Starts playwright and the browser as configured.
     *
     * @return the browser and whether it is an attached one, null when the start failed. Null rather
     * than a thrown exception, so [withBrowser] has one place that turns a startup problem into a
     * message for the caller.
     */
    private fun startBrowser(): Pair<Browser, Boolean>? = try {
        val driver = playwright ?: Playwright.create().also { playwright = it }

        if (cdpEndpoint.isNotBlank()) {
            // the browser the user started themselves, whose session is the point of attaching
            driver.chromium().connectOverCDP(cdpEndpoint) to true
        } else {
            val options = BrowserType.LaunchOptions().setHeadless(headless)
            if (executablePath.isNotBlank()) options.setExecutablePath(pathOf(executablePath))
            proxy()?.let { options.setProxy(it) }
            driver.chromium().launch(options) to false
        }
    } catch (e: PlaywrightException) {
        startupFailure = e
        null
    }

    /** the proxy the browser is to use, null for a direct connection */
    private fun proxy(): Proxy? =
        proxySocks.takeIf { it.isNotBlank() }
            ?.let { Proxy(it) }

    /**
     * The navigation options of every read.
     *
     * `LOAD` rather than the default `NETWORKIDLE`: a page that keeps a connection open, which an
     * analytics script or a websocket does, never reaches network idle and would time out every call
     * on it. Load is the point at which the dom is there and the scripts of the first round have run,
     * which is what the lazy loading of images is waited for by [scroll].
     */
    private fun navigateOptions(): Page.NavigateOptions =
        Page.NavigateOptions()
            .setTimeout(timeoutMs)
            .setWaitUntil(WaitUntilState.LOAD)

    /**
     * Waits for [selector] on [page] to reach [state], or gives up with the navigation timeout.
     *
     * [state] is left unset when the caller named none, rather than being defaulted to
     * [WaitForSelectorState.VISIBLE] here, so a caller that says nothing about the state gets
     * exactly what playwright gives by default, which is what every caller of this class relied on
     * before there was a state to pick.
     *
     * The state is the whole point of this method. Visible is not the same question as attached: an
     * element is visible when it has a non empty bounding box and is not hidden, and the container a
     * lazy loading gallery fills has neither of those until something is in it. A caller waiting for
     * that container to be visible is waiting for the data and not for the page, which is a longer
     * wait than it looks, because a gallery that has run out of items never grows that box at all.
     * Attached asks the weaker question that "the application built this" and leaves the data to
     * [scroll], which is the part that actually pulls the items in.
     */
    private fun waitFor(page: Page, selector: String, state: WaitForSelectorState?) {
        val options = Page.WaitForSelectorOptions().setTimeout(timeoutMs)
        state?.let { options.setState(it) }
        page.waitForSelector(selector, options)
    }

    /**
     * Scrolls [page] to its bottom [times] times, pausing in between.
     *
     * The pause is what makes this work: a lazy loading gallery appends its images asynchronously, so
     * scrolling as fast as the protocol allows would read the dom before anything arrived. Scrolling
     * at all is not needed for a page that shows everything at once, so [times] of 0 skips it and
     * keeps the cheap case cheap.
     *
     * Scrolled through every scrollable element rather than only the window, because an application
     * framework usually puts its list in a div with its own scrollbar: the document stays as tall as
     * the viewport and `window.scrollTo` on it does nothing at all, which would leave an angular
     * infinite scroll at the first page. Each element is scrolled in turn and the loop stops early
     * once none of them moved, so the extra work costs one round trip once the list has run out.
     */
    private fun scroll(page: Page, times: Int) {
        if (times <= 0) return
        repeat(times) {
            val moved = page.evaluate(SCROLL_SCRIPT)
            page.waitForTimeout(scrollPauseMs)
            // nothing scrolled any further, so there is nothing left to load
            if (moved == false) return
        }
    }

    /**
     * The message of [e], plus the one hint worth adding when the browser itself is what is missing.
     */
    private fun reasonOf(e: PlaywrightException?): String {
        val message = (e ?: startupFailure)?.message?.takeIf { it.isNotBlank() }
            ?: "the browser could not be started"
        return when {
            message.contains("Executable doesn't exist") || message.contains("Looks like") ->
                "$message - the browser binaries are missing, run 'gradlew installPlaywrightBrowsers'"
            // the raw message of a timeout is a multi line playwright dump with a node stack in it,
            // which tells a caller who passed a selector nothing about the selector they got wrong.
            // The call log holds the one line that does, so it is what is answered instead
            message.contains("Timeout") && message.contains("ms exceeded") ->
                timeoutOf(message)
            else -> message
        }
    }

    /**
     * The one useful line of a playwright timeout, i.e. what it was waiting for, on its own.
     *
     * The stack is dropped and the timeout itself is named, so a caller can tell "the page never got
     * this element" from "the page never finished loading" without reading the raw dump.
     */
    private fun timeoutOf(message: String): String {
        val waited = message.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("- ") || it.startsWith("waiting for") }
            ?.removePrefix("- ")
            ?: "the page did not get ready in time"

        return "the page did not get ready within ${timeoutMs.toLong()}ms: $waited"
    }

    /**
     * Whether [e] means the browser is gone rather than that the page misbehaved.
     *
     * A closed connection or a crashed target, which is what a browser that was killed or that ran
     * out of memory reports. A navigation timeout is not one of those: the browser is fine, the page
     * was just too slow, and throwing the browser away would cost a restart per slow gallery.
     */
    private fun isBrowserGone(e: PlaywrightException): Boolean =
        e.message?.contains("Target page, context or browser has been closed") == true ||
            e.message?.contains("Browser closed") == true ||
            e.message?.contains("has been closed") == true

    /** The directory [target] lives in, created when it is not there yet. */
    private fun createParentDirectory(target: File) {
        val parent = target.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory)
            throw NotAccessibleException("could not create the directory ${parent.path}")
    }

    /** The last segment of [urlString] without its query, as a file name. */
    private fun fileNameOf(urlString: String): String {
        val name = urlString.split("/").last().substringBefore("?")
        return name.ifBlank { "download" }
    }

    /**
     * The configured browser binary as a path, or null when there is none to point at.
     *
     * Null rather than an exception on a path that does not exist, so the browser gets started
     * without an executable path and reports the real problem itself, which is a far better message
     * than one about a configured value.
     */
    private fun pathOf(executable: String): Path? =
        Paths.get(executable).takeIf { Files.isRegularFile(it) }

    private companion object {

        /** how long to wait after a scroll, so a lazy loading gallery has appended its images */
        const val scrollPauseMs = 500.0

        /**
         * Collects the image urls of the rendered dom, in the page, as a plain array of strings.
         *
         * `currentSrc` first, since that is the file the browser really fetched and the only one that
         * resolves a `srcset` to one url. The lazy loading attributes are in the chain because a
         * gallery that keeps `src` on a placeholder never fills `currentSrc`, and its images are
         * exactly the ones this browser was added for.
         */
        const val IMAGE_URL_SCRIPT =
            """() => Array.from(document.images).map(i =>
                i.currentSrc || i.src || i.getAttribute('data-src')
                || i.getAttribute('data-original') || i.getAttribute('data-lazy-src')
            )"""

        /**
         * Scrolls every scrollable thing on the page to its bottom and answers whether any of them
         * moved.
         *
         * The window plus every element that is taller than it can show, which is where an
         * application framework keeps a list: the document itself often never scrolls at all on such
         * a page, so a `window.scrollTo` alone would leave an infinite scroll on its first page and
         * every further round would be a no-op.
         *
         * `scrollHeight` compared before and after rather than a scroll event listened for, because
         * what matters is not that something scrolled but that the page got taller, i.e. that
         * something was appended. Answering that is what lets the caller stop instead of scrolling a
         * page that has run out.
         */
        const val SCROLL_SCRIPT =
            """() => {
                const targets = [document.scrollingElement, document.body, document.documentElement]
                    .concat(Array.from(document.querySelectorAll('div, main, section, ul')))
                    .filter((e, i, all) => e && all.indexOf(e) === i
                        && e.scrollHeight > e.clientHeight + 10);
                let moved = false;
                for (const e of targets) {
                    const before = e.scrollTop;
                    e.scrollTop = e.scrollHeight;
                    if (e.scrollTop > before) moved = true;
                }
                return moved;
            }"""
    }
}
