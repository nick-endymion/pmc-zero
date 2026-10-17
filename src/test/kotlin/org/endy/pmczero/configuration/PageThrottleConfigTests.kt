package org.endy.pmczero.configuration

import org.endy.pmczero.service.DownloadThrottle
import org.endy.pmczero.service.PageThrottle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [PageThrottleConfig], the one place where the configured interval over pages is read.
 *
 * Everything else about that throttle is in [PageThrottle], so these are only about the value that
 * arrives: a property of 4 has to end up as 4, and one that is not there has to leave the pages as fast
 * as they were. The property is its own rather than the one of the download throttle, so what has to be
 * shown here is that configuring one leaves the other alone.
 */
class PageThrottleConfigTests {

    @BeforeEach
    fun setUp() {
        PageThrottle.reset()
        DownloadThrottle.reset()
    }

    @AfterEach
    fun tearDown() {
        // both throttles are process wide, so a configured value would outlive this class
        PageThrottle.reset()
        DownloadThrottle.reset()
    }

    @Test
    fun `hands the configured seconds to the throttle`() {
        PageThrottleConfig(4).afterPropertiesSet()

        assertEquals(4, PageThrottle.seconds)
    }

    /** The property is missing rather than empty, so the default is what an unconfigured app gets. */
    @Test
    fun `leaves the pages unthrottled when nothing is configured`() {
        PageThrottleConfig(0).afterPropertiesSet()

        assertEquals(0, PageThrottle.seconds)
    }

    /** Its own property, so the files of a run are paced by the one that paces files and not by this. */
    @Test
    fun `leaves the downloads as they were`() {
        DownloadThrottleConfig(5).afterPropertiesSet()

        PageThrottleConfig(4).afterPropertiesSet()

        assertEquals(5, DownloadThrottle.seconds)
        assertEquals(4, PageThrottle.seconds)
    }
}