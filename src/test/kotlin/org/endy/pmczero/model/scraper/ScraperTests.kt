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
        var mediuam = Medium()
        var location = Location().also { it.name = "http://"; it.storage = Storage() }

        every {
            locationService.getLocationStartingWith(any())
        } returns location

        every {
            downloader.getAsString(any())
        } returns "<html><title>TEST</title></html>"


//        val mdomParser = mockkClass(DomParser::class)
//        every { mdomParser.getElements("downloadedStuff") } returns  listOf("http://cdn.com")
        var scraper = Scraper(locationService, downloader)

        var structuredWorker = scraper.scanner.worker as StructuredWorker

//        var x = object : HtmlParser() {
//            override fun getElements(text: String): List<String> {
//                return listOf("Der Titel")
//            }
//        }
//        var y = object : HtmlParser() {
//            override fun getElements(text: String): List<String> {
//                return listOf("http://cdn.de/a.txt")
//            }
//        }
//        structuredWorker.scanners[0].htmlParser = x
//        structuredWorker.scanners[1].htmlParser = y


//        var x = scraper.scanner.worker as StructuredWorker
//        var parser = spyk( x.scanners[1].htmlParser as DomParser)
//        every {
//            parser.getElements(anyString())
//        } returns listOf("http://cdn.com")
        var sc = scraper.scan("http://testfatest.com")

        assertEquals("Der Titel", sc.set?.name)
        val media = sc.set?.media
        assertEquals(1, media?.size)
        assertEquals("a.txt", media!![0].name)
        val bessources = media!![0].bessources
        assertEquals("cdn.de/a.txt", bessources!![0].name)

    }

}
