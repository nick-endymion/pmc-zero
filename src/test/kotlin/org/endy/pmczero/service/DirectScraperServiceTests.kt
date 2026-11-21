package org.endy.pmczero.service

import org.endy.pmczero.exception.BadRequestException
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.model.scraper.direct.DirectScraper
import org.endy.pmczero.model.scraper.direct.Fa
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DirectScraperServiceTests {

    private val locationService: LocationService = mock()
    private val downloader = Downloader()
    private val service = DirectScraperService(
        locationService,
        downloader,
        listOf(Fa(downloader))
    )

    @Test
    fun `initializes a scanning context for a location`() {
        val location = Location().apply {
            id = 42
            uri = "C:/media"
            storage = Storage()
        }
        whenever(locationService.findById(42)).thenReturn(location)

        val context = service.initializeScanningKontext(42, "gallery")

        assertSame(location, context.location)
        assertSame(downloader, context.fetcher)
        assertEquals("gallery", context.locationPath)
        assertNull(context.mset)
    }

    @Test
    fun `initializes the Fa direct scraper by name`() {
        assertTrue(service.initializeDirectScraper("Fa") is Fa)
    }

    @Test
    fun `matches a direct scraper name without regard to case or surrounding spaces`() {
        assertTrue(service.initializeDirectScraper("  fa  ") is Fa)
    }

    @Test
    fun `refuses an unknown direct scraper name`() {
        assertThrows<BadRequestException> {
            service.initializeDirectScraper("unknown")
        }
    }

    @Test
    fun `creates a scraper from another implementation without changing the service`() {
        val extensibleService = DirectScraperService(locationService, downloader, listOf(Other()))

        assertTrue(extensibleService.initializeDirectScraper("Other") is Other)
    }

    private class Other : DirectScraper {
        override fun load(url: String, scanningKontext: ScanningKontext) = Unit
    }
}
