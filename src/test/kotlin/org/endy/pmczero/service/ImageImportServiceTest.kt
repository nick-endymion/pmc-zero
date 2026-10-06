package org.endy.pmczero.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [ImageImportService], the import of the images of a page as media.
 *
 * The browser is the only collaborator replaced here: [BrowserFetcher.imageUrls] answers the urls the
 * rendered page holds and [BrowserFetcher.downloadTo] writes bytes like a download would. What is
 * tested is everything between those two calls, which is where the decisions live: where a file goes,
 * what it is called, what is skipped and what a single failing image does to the rest.
 *
 * Mockk rather than mockito, because [BrowserFetcher] and [LocationService] are final classes and the
 * inline mock maker is not on the classpath.
 */
class ImageImportServiceTest {

    @TempDir
    lateinit var locationFolder: File

    private lateinit var browserFetcher: BrowserFetcher
    private lateinit var locationService: LocationService
    private lateinit var bessourceRepository: BessourceRepository
    private lateinit var msetService: MsetService
    private lateinit var service: ImageImportService

    private lateinit var location: Location

    @BeforeEach
    fun setUp() {
        browserFetcher = mockk()
        locationService = mockk()
        bessourceRepository = mockk()
        msetService = mockk()

        location = Location().also {
            it.id = 1
            it.name = "pictures"
            it.uri = locationFolder.path
            it.locationType = LocationType.MAIN_FS.i
            it.storage = Storage().also { storage -> storage.id = 5 }
        }

        every { locationService.findById(1) } returns location
        every { locationService.isFileSystemAccessible(1) } returns true
        every { bessourceRepository.findNamesOfExistingMedia(any(), any(), any()) } returns emptyList()
        // a download that writes bytes like the real one, directory included: Fetcher.downloadTo
        // promises to create the directory its target lives in, and a caller places files into a
        // folder that does not exist yet
        every { browserFetcher.downloadTo(any(), any()) } answers {
            val target = secondArg<File>()
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(1, 2, 3))
            target
        }
        every { msetService.save(any()) } answers { firstArg<Mset>().also { it.id = 7 } }

        service = ImageImportService(browserFetcher, locationService, bessourceRepository, msetService)
    }

    private fun givenPage(vararg urls: String) {
        every { browserFetcher.imageUrls(any(), any(), any()) } returns urls.toList()
    }

    /** the file [relative] is put into the location folder beforehand, as a previous import left it */
    private fun givenExistingFile(relative: String) {
        File(locationFolder, relative).apply {
            parentFile?.mkdirs()
            writeText("the file of an earlier run")
        }
    }

    @Test
    fun `writes one file per image and records a medium for each`() {
        givenPage("https://example.org/a.jpg", "https://example.org/b.jpg")

        val result = service.import(locationId = 1, url = "https://example.org", name = "gallery")

        assertEquals(2, result.imported)
        assertEquals(0, result.failed)
        assertEquals(2, result.found)
        assertEquals(7, result.msetId)

        // the files went into the folder of the set, below the location
        assertTrue(File(locationFolder, "gallery/a.jpg").isFile)
        assertTrue(File(locationFolder, "gallery/b.jpg").isFile)

        val medium = result.media[0]
        assertEquals("a.jpg", medium.name)
        // the type comes from the extension rather than being fixed, see Mtype.of
        assertEquals(Mtype.PHOTO.i, medium.mtype)
        assertEquals(RessType.PRIMARY.i, medium.bessources[0].ressType)
        // the bessource is named relative to the location, which is what LocationService.url needs to
        // build a working url from it
        assertEquals("gallery/a.jpg", medium.bessources[0].name)
        assertEquals(5, medium.bessources[0].storageId)
    }

    @Test
    fun `names two images of the same file name apart`() {
        // two urls that differ, so neither the browser's distinct() nor anything else drops one, but
        // whose last path segment is the same, which is what galleries do
        givenPage("https://example.org/thumbs/1.jpg", "https://example.org/full/1.jpg")

        val result = service.import(locationId = 1, url = "https://example.org", name = "gallery")

        assertEquals(2, result.imported)
        assertEquals(listOf("1.jpg", "1.1.jpg"), result.media.map { it.name })
        assertTrue(File(locationFolder, "gallery/1.jpg").isFile)
        assertTrue(File(locationFolder, "gallery/1.1.jpg").isFile)
    }

    @Test
    fun `leaves a file alone that is in the location already`() {
        givenExistingFile("gallery/a.jpg")
        givenPage("https://example.org/a.jpg")

        val result = service.import(locationId = 1, url = "https://example.org", name = "gallery")

        assertEquals(1, result.skipped)
        assertEquals(0, result.imported)
        // nothing was fetched for it, which is the point of skipping
        verify(exactly = 0) { browserFetcher.downloadTo(any(), any()) }
        // and nothing was saved, so an import that changed nothing leaves no empty set behind
        verify(exactly = 0) { msetService.save(any()) }
    }

    @Test
    fun `fetches a file again when skipping is switched off`() {
        givenExistingFile("gallery/a.jpg")
        givenPage("https://example.org/a.jpg")

        val result = service.import(
            locationId = 1, url = "https://example.org", name = "gallery", skipExisting = false
        )

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `keeps the other images when one cannot be fetched`() {
        every { browserFetcher.downloadTo("https://example.org/gone.jpg", any()) } throws
                NotAccessibleException("https://example.org/gone.jpg answered 404 Not Found")
        givenPage("https://example.org/a.jpg", "https://example.org/gone.jpg", "https://example.org/c.jpg")

        val result = service.import(locationId = 1, url = "https://example.org", name = "gallery")

        // the two that could be had are imported, so one broken url does not cost the whole gallery
        assertEquals(2, result.imported)
        assertEquals(1, result.failed)
        assertEquals("https://example.org/gone.jpg", result.failures[0].url)
        assertTrue(result.failures[0].reason.contains("404"))
        assertTrue(File(locationFolder, "gallery/c.jpg").isFile)
    }

    @Test
    fun `does not save anything when persist is off`() {
        givenPage("https://example.org/a.jpg")

        val result = service.import(
            locationId = 1, url = "https://example.org", name = "gallery", persist = false
        )

        // the file is written either way, so the draft can be looked at, but no row points at it
        assertEquals(1, result.imported)
        assertEquals(null, result.msetId)
        verify(exactly = 0) { msetService.save(any()) }
    }

    @Test
    fun `reports a page without images rather than importing nothing`() {
        givenPage()

        assertThrows<NotAccessibleException> {
            service.import(locationId = 1, url = "https://example.org", name = "gallery")
        }
    }

    @Test
    fun `refuses a location that cannot hold files`() {
        location.locationType = LocationType.MAIN_HTTP.i
        givenPage("https://example.org/a.jpg")

        val failure = assertThrows<NotAccessibleException> {
            service.import(locationId = 1, url = "https://example.org", name = "gallery")
        }

        // a http location has no directory, so the refusal has to happen before anything is fetched
        assertTrue(failure.message!!.contains("MAIN_FS"))
        verify(exactly = 0) { browserFetcher.downloadTo(any(), any()) }
    }

    @Test
    fun `takes only the urls matching the pattern`() {
        every { browserFetcher.imageUrls(any(), any(), any()) } returns listOf(
            "https://example.org/thumbs/a.jpg",
            "https://example.org/full/a.jpg",
            "https://cdn.other.org/b.jpg"
        )

        val result = service.import(
            locationId = 1,
            url = "https://example.org",
            name = "gallery",
            pattern = "/full/"
        )

        assertEquals(listOf("a.jpg"), result.media.map { it.name })
    }

    @Test
    fun `leaves out the data uris of inline images`() {
        every { browserFetcher.imageUrls(any(), any(), any()) } returns listOf(
            "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
            "https://example.org/a.jpg"
        )

        val result = service.import(locationId = 1, url = "https://example.org", name = "gallery")

        // a data uri is not a file, so it is dropped before anything is fetched rather than being
        // written out as an image whose bytes are its own markup
        assertEquals(1, result.imported)
        assertEquals(0, result.failed)
        assertTrue(File(locationFolder, "gallery/a.jpg").isFile)
    }

    @Test
    fun `hands the wait selector to the browser`() {
        givenPage("https://example.org/a.jpg")

        service.import(
            locationId = 1,
            url = "https://example.org",
            name = "gallery",
            waitForSelector = ".location-row"
        )

        // a single page application needs one, since without it the browser reads the dom before the
        // app has built anything; it has to reach the fetcher rather than being dropped here
        verify { browserFetcher.imageUrls(any(), any(), ".location-row") }
    }

    @Test
    fun `collects without a wait selector on a page that needs none`() {
        givenPage("https://example.org/a.jpg")

        service.import(locationId = 1, url = "https://example.org", name = "gallery")

        verify { browserFetcher.imageUrls(any(), any(), null) }
    }
}
