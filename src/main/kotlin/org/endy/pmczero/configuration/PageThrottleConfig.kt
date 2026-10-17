package org.endy.pmczero.configuration

import org.endy.pmczero.service.PageThrottle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Hands the configured throttle over the pages a scrape reads to [PageThrottle], which cannot read a
 * property itself.
 *
 * A tiny class with one job, and it exists for the reason
 * [org.endy.pmczero.configuration.DownloadThrottleConfig] gives: the worker that waits on it
 * ([org.endy.pmczero.model.scraper.StructuredWorker]) is built by kotlinx.serialization when a stored
 * scraper is read back and is handed nothing at all, so there is nowhere to inject a collaborator into
 * it and the throttle has to be reached through a static. Reading the property out of the static itself
 * is not an option either, since there is no spring context in it. So the value is pushed in from here
 * once at startup, which is the other end of the same arrangement.
 *
 * Its own property rather than the one of the download throttle, so a deployment can pace its pages
 * without pacing its files, or the other way round. See [PageThrottle].
 *
 * The default is 0, i.e. no throttling, so an application that configures nothing reads its pages
 * exactly as fast as it did before this existed.
 */
@Component
class PageThrottleConfig(
    @Value("\${pmc.page.throttle-seconds:0}") private val throttleSeconds: Long
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(PageThrottleConfig::class.java)

    /**
     * Puts the configured value where [PageThrottle.awaitTurn] reads it, and says so once at startup,
     * since a run that suddenly takes minutes longer is otherwise only visible in the files of the next
     * gallery that lands.
     */
    override fun afterPropertiesSet() {
        PageThrottle.seconds = throttleSeconds

        logger.info(
            if (throttleSeconds > 0)
                "the pages a scrape fetches for itself are kept ${throttleSeconds}s apart"
            else
                "no throttle on the pages a scrape fetches is configured"
        )
    }
}