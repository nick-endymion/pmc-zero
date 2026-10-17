package org.endy.pmczero.configuration

import org.endy.pmczero.service.DownloadThrottle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Hands the configured throttle to [DownloadThrottle], which cannot read a property itself.
 *
 * A tiny class with one job, and it exists only because [DownloadThrottle] is an object rather than a
 * bean: the worker that waits on it
 * ([org.endy.pmczero.model.scraper.FileDownloader]) is built by kotlinx.serialization when a stored
 * scraper is read back and is handed nothing at all, so there is nowhere to inject a collaborator
 * into it and the throttle has to be reached through a static. Reading the property out of the static
 * itself is not an option either, since there is no spring context in it. So the value is pushed in
 * from here once at startup, which is the other end of the same arrangement.
 *
 * The default is 0, i.e. no throttling, so an application that configures nothing downloads exactly
 * as fast as it did before this existed.
 */
@Component
class DownloadThrottleConfig(
    @Value("\${pmc.download.throttle-seconds:0}") private val throttleSeconds: Long
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(DownloadThrottleConfig::class.java)

    /**
     * Puts the configured value where [DownloadThrottle.awaitTurn] reads it, and says so once at
     * startup, since a scrape that suddenly takes an hour longer is otherwise only visible in the
     * files of the next gallery that lands.
     */
    override fun afterPropertiesSet() {
        DownloadThrottle.seconds = throttleSeconds

        logger.info(
            if (throttleSeconds > 0)
                "the downloaded files of a scan are kept ${throttleSeconds}s apart"
            else
                "no throttle on the downloaded files is configured"
        )
    }
}