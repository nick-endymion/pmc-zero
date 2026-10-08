package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Scanner
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.repository.SerializedScannerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for [ScannerService], and for the copy in particular.
 *
 * The repository is mocked rather than driven, since what is under test here is which fields a copy
 * carries over and which of them it leaves behind: a copy that took the id of its original would be an
 * edit, and a copy that dropped the serialization would be a name with nothing behind it.
 */
class ScannerServiceTests {

    private val serializedScannerRepository: SerializedScannerRepository = mock()

    private lateinit var service: ScannerService

    /** The scraper a stored scanner holds: title, images recorded, images downloaded. */
    private fun imageScraper(): Scraper = Scraper(
        PassThroughParser(),
        StructuredWorker(
            download = false,
            scrapers = listOf(
                Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder()),
                Scraper(DomParser("(.+)", "img[src]", "abs:src"), RecoveryWorker(FileDownloader()))
            )
        )
    )

    /** A scanner row as the repository would hand it back, serializable and complete. */
    private fun givenScanner(id: Int, name: String): Scanner = Scanner().also {
        it.id = id
        it.name = name
        it.regex = "https://example\\.org/.*"
        it.supplierIdentifcator = "https://example\\.org/artikel/([0-9]+)/.*"
        it.example = "https://example.org/gallery"
        it.serialization = service.serialize(imageScraper())
        it.valid = true
    }

    /**
     * The scanner stored under [id], read the way [ScannerService.findById] reads it.
     *
     * Stubbed on `findById` rather than on `findByIdOrNull`, since the latter is an extension whose body
     * calls the former: a stub on the extension is registered on the `findById` that runs inside it, and
     * then answers a Scanner where the method is declared to return an [Optional].
     */
    private fun givenStoredScanner(id: Int, name: String): Scanner =
        givenScanner(id, name).also { whenever(serializedScannerRepository.findById(id)).thenReturn(Optional.of(it)) }

    @BeforeEach
    fun setUp() {
        service = ScannerService(
            serializedScannerRepository,
            mock<ScraperService>(),
            mock<LocationService>(),
            mock<BookmarkService>()
        )

        // a save assigns an id the way the database would, since the point of the copy is that it is a
        // new row: without this every save would hand back the scanner it was given and a test could
        // not tell a copy from an edit
        whenever(serializedScannerRepository.save(any<Scanner>())).thenAnswer { invocation ->
            (invocation.getArgument<Scanner>(0)).also { if (it.id == null) it.id = 99 }
        }
    }

    // -------------------------------------------------------------------------------------
    // Copying
    // -------------------------------------------------------------------------------------

    @Test
    fun `copies a scanner into a new one`() {
        givenStoredScanner(3, "Image Scraper")

        val copy = service.copy(3)

        assertEquals(99, copy.id)
        assertEquals("Image Scraper (copy)", copy.name)
    }

    /** The whole point: the copy is its own row, and the original keeps the id it had. */
    @Test
    fun `leaves the original where it was`() {
        val original = givenStoredScanner(3, "Image Scraper")

        val copy = service.copy(3)

        assertNotEquals(original.id, copy.id)
        assertEquals(3, original.id)
    }

    /** The scraper is the copy, so an unchanged copy runs exactly what the original runs. */
    @Test
    fun `carries the scraper of the original over`() {
        givenStoredScanner(3, "Image Scraper")

        val copy = service.copy(3)

        val scraper = service.deserialize(copy.serialization!!)
        val structured = scraper.worker as StructuredWorker
        assertEquals(3, structured.scrapers.size)
        assertEquals("img[src]", (structured.scrapers[1].parser as DomParser).tag)
    }

    @Test
    fun `carries the url regex of the original over`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals("https://example\\.org/.*", service.copy(3).regex)
    }

    @Test
    fun `carries the example of the original over`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals("https://example.org/gallery", service.copy(3).example)
    }

    /**
     * A copy that lost this would be a scanner nobody can tell what a url of it belongs to, which is
     * the one thing a copy of a scanner is for.
     */
    @Test
    fun `carries the supplier identifcator of the original over`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals(
            "https://example\\.org/artikel/([0-9]+)/.*",
            service.copy(3).supplierIdentifcator
        )
    }

    /** A copy that came back invalid would not be offered by findByUrl, so it would be unreachable. */
    @Test
    fun `stores the copy as valid`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals(true, service.copy(3).valid)
    }

    // -------------------------------------------------------------------------------------
    // Naming the copy
    // -------------------------------------------------------------------------------------

    @Test
    fun `names the copy after the one it came from`() {
        givenStoredScanner(3, "Gallery Index Lister")

        assertEquals("Gallery Index Lister (copy)", service.copy(3).name)
    }

    @Test
    fun `takes the name it was given`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals("Nur grosse Bilder", service.copy(3, "Nur grosse Bilder").name)
    }

    /** A blank name is no name, and a scanner without one cannot be told apart in a list. */
    @Test
    fun `falls back to the default name when the given one is blank`() {
        givenStoredScanner(3, "Image Scraper")

        assertEquals("Image Scraper (copy)", service.copy(3, "   ").name)
    }

    /** The original may itself be unnamed, which is what a scanner built by hand often is. */
    @Test
    fun `names a copy of an unnamed scanner without failing`() {
        givenStoredScanner(3, "Scraper").name = null

        assertEquals("null (copy)", service.copy(3).name)
    }

    // -------------------------------------------------------------------------------------
    // What a copy is stored as
    // -------------------------------------------------------------------------------------

    /**
     * Through [ScannerService.save], so the serialization is deserialized and written back.
     *
     * Two saves rather than one, since that is what save does: the row is written first and then
     * written again with the normalized serialization. It is also what a copy of a scraper this
     * application cannot read has to go through, since it is refused there rather than stored.
     */
    @Test
    fun `stores the copy the way every other scanner is stored`() {
        givenStoredScanner(3, "Image Scraper")

        service.copy(3)

        verify(serializedScannerRepository, times(2)).save(any())
    }

    @Test
    fun `does not delete or update the original`() {
        val original = givenStoredScanner(3, "Image Scraper")

        service.copy(3)

        verify(serializedScannerRepository, times(0)).delete(any<Scanner>())
        // the row saved is the copy, which is a different object from the one that was read
        val captor = argumentCaptor<Scanner>()
        verify(serializedScannerRepository, times(2)).save(captor.capture())
        assertNotNull(captor.firstValue.id)
        assertEquals(original.serialization, captor.firstValue.serialization)
    }

    @Test
    fun `answers not found for an unknown scanner`() {
        whenever(serializedScannerRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.copy(99)
        }
    }

    /** Nothing is written for a scanner that does not exist, rather than an empty copy being stored. */
    @Test
    fun `stores nothing for an unknown scanner`() {
        whenever(serializedScannerRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.copy(99)
        }

        verify(serializedScannerRepository, times(0)).save(any())
    }
}
