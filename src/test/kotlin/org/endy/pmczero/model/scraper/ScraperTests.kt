package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.getBaseUrl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScraperTests {

    @MockK
    public lateinit var locationService: LocationService

    @MockK
    public lateinit var downloader: Downloader

    @MockK
    public lateinit var locationRepository: LocationRepository

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun test() {

        var location = Location().also { it.name = "http://"; it.storage = Storage() }

//        every {
//            locationService.getLocationStartingWith(any())
//        } returns location

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

    @Test
    fun locationTest() {
        val locationService2 = LocationService(locationRepository)
        var urls = listOf("https://abc.de/abde/aaaa.html", "https://abc.de/abde/aaeea.html","https://abc.de/abde/waaa.html")
        var commonUrl = locationService2.getCommonStart(urls)
        println(commonUrl)
        println(commonUrl.getBaseUrl())
        urls = listOf("https://abc.de/abxe/aaaa.html", "https://abc.de/abde/aaeea.html","https://abc.de/abde/waaa.html")
        commonUrl = locationService2.getCommonStart(urls)
        println(commonUrl)
        println(commonUrl.getBaseUrl())
        urls = listOf("https://abc.com/abxe/aaaa.html", "https://abc.de/abde/aaeea.html","https://abc.de/abde/waaa.html")
        commonUrl = locationService2.getCommonStart(urls)
        println(commonUrl)
        println(commonUrl.getBaseUrl())
    }

    fun setScanner(scraper: Scraper) {

        scraper.scanner = Scanner(
            RegexParser("(.*fa.*)"),
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
