package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.mockk.spyk
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import kotlin.test.assertEquals

class ScraperTests {

    @MockK
    public lateinit var locationService: LocationService

    @MockK
    public lateinit var downloader: Downloader

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun test() {

        var location = Location().also { it.name = "http://"; it.storage = Storage() }

        every {
            locationService.getLocationStartingWith(any())
        } returns location

        every {
            downloader.getAsString(any())
        } returns "<html><title>Der Titel</title><a>http://aaa.de/link</a></html>"

        var scraper = Scraper(locationService, downloader)

        setScanner(scraper)

        var sc = scraper.scan("http://testfatest.com")

        assertEquals("Der Titel", sc.set?.name)
        val media = sc.set?.media
        assertEquals(1, media?.size)
        assertEquals("link", media!![0].name)
        val bessources = media!![0].bessources
        assertEquals("aaa.de/link", bessources!![0].name)

    }

    fun setScanner(scraper: Scraper) {

        scraper.scanner = Scanner(
            SimpleParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scanner(DomParser("(.*)", "title", ""), SetCreator()),
                    Scanner(
                        DomParser("(.*)", "a", ""), MediaAdder()
                    )
                )
            )
        )

    }

}
