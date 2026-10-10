package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
     * A scan with a location and a path writes every image of the page into that path.
     *
     * [FileDownloader] is verified on its own in `FileDownloaderTests`; what is new here is that the
     * path survives the trip from the [org.endy.pmczero.service.ScraperService.scan] call into the
     * kontext the workers are handed.
     *
     * The page title is still picked up, so the set is named as before: the path belongs to the
     * kontext and is of no interest to a worker that has no use for it.
     *
     * No [MediaAdder] here, on purpose rather than out of caution: it names a bessource after the
     * element minus the uri of the location, so it only works against a location whose uri is the
     * url prefix of the elements, and it throws on a file system location whose path is no prefix at
     * all. That is a pre-existing limitation of that worker and it does not belong in this test.
     */
    @Test
    fun `a scan with a location path downloads every image into that path`() {
        val page = """
            <html><head><title>Galerie</title></head><body>
            <img src="/bilder/erstes.jpg">
            <img src="zweites.jpg">
            <img src="drittes.png">
            </body></html>
        """.trimIndent()

        every { downloader.getAsString(any()) } returns page

        val target = Files.createTempDirectory("scan").toFile()
        target.deleteOnExit()
        val location = fsLocation(7, target)
        every { locationService.findById(7) } returns location
        givenDownloaderWritesFiles()

        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser("(.+)", "img", "abs:src"), FileDownloader())
                )
            )
        )

        val sc = ScraperService(locationService, downloader, browserFetcher)
            .scan(scanner, "http://testfatest.com/galerie.html", locationId = 7, locationPath = "2020/august")

        // the path got as far as the kontext the workers are handed
        assertEquals("2020/august", sc.locationPath)
        assertEquals("Galerie", sc.mset?.name)

        // and every image of the page landed inside it
        val written = target.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(target).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

        assertEquals(
            listOf("2020/august/drittes.png", "2020/august/erstes.jpg", "2020/august/zweites.jpg"),
            written
        )

        // through the downloader of the service, i.e. the fetcher of the kontext, so the files and the
        // page came from the same place
        verify(exactly = 3) { downloader.downloadTo(any(), any(), any()) }
    }

    /**
     * The same scan without a path puts its files into the location root, so the path is an addition
     * to the existing behaviour rather than a replacement of it.
     */
    @Test
    fun `a scan without a location path downloads into the location root`() {
        every { downloader.getAsString(any()) } returns
                "<html><body><img src='/bilder/erstes.jpg'></body></html>"

        val target = Files.createTempDirectory("scan-root").toFile()
        target.deleteOnExit()
        every { locationService.findById(8) } returns fsLocation(8, target)
        givenDownloaderWritesFiles()

        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(Scraper(DomParser("(.+)", "img", "abs:src"), FileDownloader()))
            )
        )

        val sc = ScraperService(locationService, downloader, browserFetcher)
            .scan(scanner, "http://testfatest.com/galerie.html", locationId = 8)

        assertEquals("", sc.locationPath)
        assertTrue(File(target, "erstes.jpg").isFile)
    }

    /**
     * A scan without a location is a catchup one, whose placeholder location has no path. Downloading
     * then has to fail rather than fall back to the working directory of the process, which is what
     * `File("")` resolves to.
     */
    @Test
    fun `a catchup scan cannot download its elements`() {
        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(Scraper(DomParser("(.+)", "img", "abs:src"), FileDownloader()))
            )
        )
        every { downloader.getAsString(any()) } returns
                "<html><body><img src='/bilder/erstes.jpg'></body></html>"

        assertFailsWith<NotAccessibleException> {
            ScraperService(locationService, downloader, browserFetcher)
                .scan(scanner, "http://testfatest.com/galerie.html")
        }

        verify(exactly = 0) { downloader.downloadTo(any(), any(), any()) }
    }

    /**
     * A full import in one scan: every image of the page is recorded as a medium and downloaded, and
     * every bessource points at a file that is really there.
     *
     * This is the combination that could not work before. [MediaAdder] named a bessource after the
     * element minus the uri of the location, which is only meaningful for a location whose uri is a
     * url prefix, so against the file system location of an import it threw
     * `StringIndexOutOfBoundsException` before recording anything.
     *
     * The last assertion is the one that matters: a medium whose bessource names a file that is not
     * there is a url that answers 404, and nothing in the scan would say why.
     */
    @Test
    fun `a scan records a medium for every image and downloads it`() {
        val page = """
            <html><head><title>Galerie</title></head><body>
            <img src="/bilder/erstes.jpg">
            <img src="zweites.jpg">
            <img src="https://cdn.de/drittes.png">
            </body></html>
        """.trimIndent()

        every { downloader.getAsString(any()) } returns page

        val target = Files.createTempDirectory("scan-both").toFile()
        target.deleteOnExit()
        every { locationService.findById(9) } returns fsLocation(9, target)
        givenDownloaderWritesFiles()

        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser("(.+)", "img", "abs:src"), MediaAdder()),
                    Scraper(DomParser("(.+)", "img", "abs:src"), FileDownloader())
                )
            )
        )

        val sc = ScraperService(locationService, downloader, browserFetcher)
            .scan(scanner, "http://testfatest.com/galerie.html", locationId = 9, locationPath = "2020/august")

        val media = sc.mset!!.media
        assertEquals(3, media.size)
        assertEquals(listOf("erstes.jpg", "zweites.jpg", "drittes.png"), media.map { it.name })

        // every bessource names a file that the scan really wrote
        for (medium in media) {
            val relative = medium.bessources.single().name!!
            assertTrue(
                File(target, relative).isFile,
                "medium ${medium.name} points at '$relative', which is not a file"
            )
        }

        // and the names sit below the path of the scan, so LocationService.url would serve them
        assertEquals(
            listOf("2020/august/erstes.jpg", "2020/august/zweites.jpg", "2020/august/drittes.png"),
            media.map { it.bessources.single().name }
        )
    }

    /**
     * A page holding the same file name twice: one file, two media on it, and the second element
     * reported as not fetched.
     *
     * A plain scan, so [ScanningKontext.alwaysNewDownload] is false, which is the default. The file
     * that is there is left alone rather than a second copy written beside it, so both media point at
     * the one file and the caller is told which of the two urls it is holding. A caller that wants both
     * files asks for it on the import path, see
     * [org.endy.pmczero.service.ScraperImageImportService.importWithStoredScanner].
     */
    @Test
    fun `a scan keeps one file when a page holds the same file name twice`() {
        val page = """
            <html><body>
            <img src="/bilder/eins/a.jpg">
            <img src="/bilder/zwei/a.jpg">
            </body></html>
        """.trimIndent()

        every { downloader.getAsString(any()) } returns page

        val target = Files.createTempDirectory("scan-clash").toFile()
        target.deleteOnExit()
        every { locationService.findById(10) } returns fsLocation(10, target)
        givenDownloaderWritesFiles()

        val sc = ScraperService(locationService, downloader, browserFetcher)
            .scan(imageScanner(), "http://testfatest.com/galerie.html", locationId = 10, locationPath = "2020")

        val media = sc.mset!!.media
        assertEquals(2, media.size)
        assertEquals(
            listOf("2020/a.jpg", "2020/a.jpg"),
            media.map { it.bessources.single().name },
            "both media point at the one file there is"
        )
        assertEquals(
            listOf("http://testfatest.com/bilder/zwei/a.jpg"),
            sc.failures.map { it.element },
            "the second url is reported as not fetched"
        )
        assertEquals(
            "content of http://testfatest.com/bilder/eins/a.jpg",
            File(target, "2020/a.jpg").readText()
        )
    }

    /**
     * The same page with [ScanningKontext.alwaysNewDownload] set: two media on two paths and two files,
     * rather than one medium whose file the second download replaced.
     *
     * The content of both files is asserted, not just their names, since "there are two files" is also
     * true when the second overwrote the first and only one name was ever taken.
     *
     * The kontext is built here rather than through [ScraperService.scan], which has no way of being
     * asked for a new download: the import path is the one that offers it, see
     * [org.endy.pmczero.service.ScraperImageImportService.importWithStoredScanner].
     */
    @Test
    fun `a scan keeps both files when a new download is asked for`() {
        val page = """
            <html><body>
            <img src="/bilder/eins/a.jpg">
            <img src="/bilder/zwei/a.jpg">
            </body></html>
        """.trimIndent()

        every { downloader.getAsString(any()) } returns page

        val target = Files.createTempDirectory("scan-clash-new").toFile()
        target.deleteOnExit()
        givenDownloaderWritesFiles()

        val kontext = ScanningKontext(
            fsLocation(10, target),
            Mset(),
            arrayListOf(),
            downloader,
            "2020",
            alwaysNewDownload = true
        )

        // the url rather than the page, which is what [ScraperService.scan] hands the scraper: the
        // outer parser picks out of the url and the worker fetches the page it names
        imageScanner().doWork("http://testfatest.com/galerie.html", "", kontext)

        val media = kontext.mset!!.media
        assertEquals(2, media.size)

        for (medium in media) {
            val relative = medium.bessources.single().name!!
            assertTrue(File(target, relative).isFile, "$relative is recorded but no file is there")
        }

        // and each file really holds the bytes of its own url, rather than the second having replaced
        // the first
        assertEquals("content of http://testfatest.com/bilder/eins/a.jpg", File(target, "2020/a.jpg").readText())
        assertEquals("content of http://testfatest.com/bilder/zwei/a.jpg", File(target, "2020/a.1.jpg").readText())
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
     * The scraper of the images of a page: every `img` recorded as a medium and downloaded.
     *
     * The worker the same pipelines of the application build, see
     * [org.endy.pmczero.service.ScraperImageImportService.scraperOf], so a test here is about the
     * pipeline rather than about a scraper of its own.
     */
    private fun imageScanner() = Scraper(
        RegexParser("(.*fa.*)"),
        StructuredWorker(
            true,
            listOf(
                Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                Scraper(DomParser("(.+)", "img", "abs:src"), MediaAdder()),
                Scraper(DomParser("(.+)", "img", "abs:src"), FileDownloader())
            )
        )
    )

    /**
     * Lets the mocked [Downloader] answer every `downloadTo` by writing the file it was handed, so a
     * test can look at what really landed on disk.
     *
     * The paths are already resolved by the time the downloader is called, so this answers without
     * looking at its arguments: the assertions that matter are about where the files are, and those
     * are read back off the file system.
     */
    private fun givenDownloaderWritesFiles() {
        every { downloader.downloadTo(any(), any(), any()) } answers {
            val target = secondArg<File>()
            target.parentFile?.mkdirs()
            target.writeText("content of ${firstArg<String>()}")
            target
        }
    }

    /** a writable MAIN_FS location pointing at [folder], which is what a scan needs to store into */
    private fun fsLocation(id: Int, folder: File) = Location().also {
        it.id = id
        it.name = "Bilder-$id"
        it.uri = folder.absolutePath
        it.locationType = LocationType.MAIN_FS.i
        it.inuse = 1
        it.storage = Storage()
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
