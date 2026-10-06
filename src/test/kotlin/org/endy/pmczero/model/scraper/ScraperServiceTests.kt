package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScraperServiceTests {

    @MockK
    public lateinit var locationService: LocationService

    @MockK
    public lateinit var downloader: Downloader

    @MockK
    public lateinit var browserFetcher: BrowserFetcher

    @MockK
    public lateinit var locationRepository: LocationRepository

    @MockK
    public lateinit var storageService: StorageService

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

        var scraper = ScraperService(locationService, downloader, browserFetcher)

        val scanner: Scraper = setScraper()

        var sc = scraper.scan(scanner,"http://testfatest.com")

        assertEquals("Der Titel", sc.mset?.name)
        val media = sc.mset?.media
        assertEquals(1, media?.size)
        assertEquals("link", media!![0].name)
        val bessources = media!![0].bessources
        assertEquals("http://aaa.de/link", bessources!![0].name)

    }

    /**
     * Every image of a gallery page ends up as a medium, and nothing else does.
     *
     * The page mixes the four ways a src can appear that a scan has to survive: absolute on the
     * scanned host, absolute on a foreign host, root relative and document relative. The relative
     * ones are only resolvable because [StructuredWorker] hands the base uri of the downloaded page
     * down to the inner scrapers, so this is the one test that would notice if that stopped
     * happening.
     *
     * Two things that are shown but are not an image have to be left out as well, and they are
     * excluded for different reasons: an `img` without a src has an empty attribute, which `(.+)`
     * filters, and a script src is not an `img` at all, which the tag selector filters.
     */
    @Test
    fun `all images shown on a page are extracted`() {

        val page = """
            <html>
            <head>
                <title>Galerie</title>
                <script src="/js/app.js"></script>
            </head>
            <body>
                <img src="/bilder/erstes.jpg" alt="hoch">
                <img src="zweites.jpg">
                <img src="https://cdn.de/drittes.png">
                <img src="viertes.jpg?size=large">
                <img alt="ohne src">
                <a href="/bilder/fuenftes.html">Link</a>
            </body>
            </html>
        """.trimIndent()

        every {
            downloader.getAsString(any())
        } returns page

        val scraper = ScraperService(locationService, downloader, browserFetcher)

        val sc = scraper.scan(setImageScraper(), "http://testfatest.com/galerie.html")

        assertEquals("Galerie", sc.mset?.name)

        val media = sc.mset!!.media

        // one medium per image, in the order of the page, named after the file
        assertEquals(listOf("erstes.jpg", "zweites.jpg", "drittes.png", "viertes.jpg"), media.map { it.name })
        assertTrue(media.all { it.mtype == Mtype.IMEDIUM.i })

        // and one bessource per medium, holding the url the image is to be fetched from. The
        // relative srcs are resolved against the page, the query string is kept here and stripped
        // from the name above.
        assertEquals(
            listOf(
                "http://testfatest.com/bilder/erstes.jpg",
                "http://testfatest.com/zweites.jpg",
                "https://cdn.de/drittes.png",
                "http://testfatest.com/viertes.jpg?size=large"
            ),
            media.flatMap { it.bessources }.map { it.name }
        )

        media.forEach { assertEquals(1, it.bessources.size) }
    }

    @Test
    fun locationTest() {
        val locationService2 = LocationService(locationRepository, storageService, mockk())
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

    fun setScraper() : Scraper {

        return Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(
                        DomParser("(.*)", "a", ""), MediaAdder()
                    )
                )
            )
        )

    }

    /**
     * Like [setScraper], but the media branch reads the src of the images instead of the text of the
     * links.
     *
     * `abs:src` rather than `src`, so jsoup resolves a relative src against the base uri the
     * [StructuredWorker] passed down, and `(.+)` rather than `(.*)`, which drops an `img` whose src
     * attribute is missing instead of adding a medium with an empty url.
     */
    fun setImageScraper(): Scraper {

        return Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(
                        DomParser("(.+)", "img", "abs:src"), MediaAdder()
                    )
                )
            )
        )

    }

}
