package org.endy.pmczero.configuration

import org.endy.pmczero.service.DownloadThrottle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [DownloadThrottleConfig], the one place where the configured interval is read.
 *
 * Everything else about the throttle is in [DownloadThrottle], so these are only about the value that
 * arrives: a property of 5 has to end up as 5, and one that is not there has to leave the downloads
 * as fast as they were.
 */
class DownloadThrottleConfigTests {

    @BeforeEach
    fun setUp() {
        DownloadThrottle.reset()
    }

    @AfterEach
    fun tearDown() {
        // the throttle is process wide, so a configured value would outlive this class
        DownloadThrottle.reset()
    }

    @Test
    fun `hands the configured seconds to the throttle`() {
        DownloadThrottleConfig(5).afterPropertiesSet()

        assertEquals(5, DownloadThrottle.seconds)
    }

    /** The property is missing rather than empty, so the default is what an unconfigured app gets. */
    @Test
    fun `leaves the downloads unthrottled when nothing is configured`() {
        DownloadThrottleConfig(0).afterPropertiesSet()

        assertEquals(0, DownloadThrottle.seconds)
    }
}