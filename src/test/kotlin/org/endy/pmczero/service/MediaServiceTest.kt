package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [MediaService.url].
 *
 * A real [LocationService] (over mocked repositories) is used on purpose: the point of these tests
 * is that url resolution goes through LocationService.providePhysicalRessources.
 */
class MediaServiceTest {

    private val mediaRepository: MediaRepository = mock()
    private val bessourceRepository: BessourceRepository = mock()
    private val locationRepository: LocationRepository = mock()
    private val storageRepository: StorageRepository = mock()

    private lateinit var service: MediaService

    @BeforeEach
    fun setUp() {
        val locationService = LocationService(locationRepository, StorageService(storageRepository))
        service = MediaService(mediaRepository, bessourceRepository, locationService)
    }

    // -------------------------------------------------------------------------------------
    // url(medium, ressType)
    // -------------------------------------------------------------------------------------

    @Test
    fun `resolves the primary bessource from the main http location`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertEquals("http://example.org/main/doc.pdf", service.url(medium, RessType.PRIMARY))
    }

    @Test
    fun `resolves a pic bessource from the main http location`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY), bessource("p.jpg", RessType.PIC))

        assertEquals("http://example.org/main/p.jpg", service.url(medium, RessType.PIC))
    }

    @Test
    fun `serves the thumbnailed bessource from the tn location`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY), bessource("thumb.jpg", RessType.TN))

        assertEquals("http://example.org/tn/thumb.jpg", service.url(medium, RessType.TN))
    }

    @Test
    fun `derives the thumbnail from the primary when the medium has no thumbnailed bessource`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertEquals("http://example.org/tn/doc.pdf", service.url(medium, RessType.TN))
    }

    @Test
    fun `falls back to the main location when the tn location is not available`() {
        // thumbnail stored next to the medium instead of in the tn location
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP)))
        val medium = medium(primary("doc.pdf", RessType.PRIMARY), bessource("thumb.jpg", RessType.TN))

        assertEquals("http://example.org/main/thumb.jpg", service.url(medium, RessType.TN))
    }

    @Test
    fun `does not fall back to another location for non thumbnail resource types`() {
        givenStorage(storage(1, location("http://example.org/tn", LocationType.TN_HTTP)))
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertThrows<Exception> {
            service.url(medium, RessType.PRIMARY)
        }
    }

    @Test
    fun `throws when the medium has no bessource of the requested type`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertThrows<Exception> {
            service.url(medium, RessType.PIC)
        }
    }

    @Test
    fun `throws when the storage has no location for the resource type`() {
        givenStorage(storage(1, location("http://example.org/tn", LocationType.TN_HTTP)))
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertThrows<Exception> {
            service.url(medium, RessType.PRIMARY)
        }
    }

    @Test
    fun `replaces the extension with jpg when the location declares one`() {
        givenStorage(httpStorage(extension = "pdf"))
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertEquals("http://example.org/main/doc.jpg", service.url(medium, RessType.PRIMARY))
    }

    @Test
    fun `resolves each bessource against the location of its own storage`() {
        givenStorage(
            storage(1, location("http://storage-1/main", LocationType.MAIN_HTTP))
        )
        givenStorage(
            storage(2, location("http://storage-2/main", LocationType.MAIN_HTTP))
        )
        val medium = medium(
            primary("a.pdf", RessType.PRIMARY, storageId = 1),
            bessource("b.pdf", RessType.PIC, storageId = 2)
        )

        assertEquals("http://storage-1/main/a.pdf", service.url(medium, RessType.PRIMARY))
        assertEquals("http://storage-2/main/b.pdf", service.url(medium, RessType.PIC))
    }

    // -------------------------------------------------------------------------------------
    // url(id, ressType)
    // -------------------------------------------------------------------------------------

    @Test
    fun `url by id loads the medium and resolves its url`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))
        whenever(mediaRepository.findById(medium.id!!)).thenReturn(Optional.of(medium))

        assertEquals("http://example.org/main/doc.pdf", service.url(medium.id!!, RessType.PRIMARY))
    }

    @Test
    fun `url by id throws NotFoundException for an unknown medium`() {
        whenever(mediaRepository.findById(404)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.url(404, RessType.PRIMARY)
        }
    }

    // -------------------------------------------------------------------------------------
    // getUrlFor(medium, ressType, locationType)
    // -------------------------------------------------------------------------------------

    @Test
    fun `getUrlFor resolves the bessource in the given location`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY), bessource("thumb.jpg", RessType.TN))

        assertEquals(
            "http://example.org/tn/thumb.jpg",
            service.getUrlFor(medium, RessType.TN, LocationType.TN_HTTP)
        )
        assertEquals(
            "http://example.org/main/thumb.jpg",
            service.getUrlFor(medium, RessType.TN, LocationType.MAIN_HTTP)
        )
    }

    @Test
    fun `getUrlFor returns null when the storage has no location of the requested type`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertNull(service.getUrlFor(medium, RessType.PRIMARY, LocationType.TN_FS))
    }

    @Test
    fun `getUrlFor returns null for an unknown bessource type`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertNull(service.getUrlFor(medium, RessType.PIC, LocationType.MAIN_HTTP))
    }

    // -------------------------------------------------------------------------------------
    // ressourceUrls(medium)
    // -------------------------------------------------------------------------------------

    @Test
    fun `ressourceUrls returns the primary and thumbnail urls`() {
        givenStorage(httpStorage())
        val medium = medium(
            primary("doc.pdf", RessType.PRIMARY),
            bessource("doc_thumb.jpg", RessType.TN)
        )

        val urls = service.ressourceUrls(medium)

        assertEquals(5, urls!!.id)
        assertEquals("medium-5", urls.name)
        assertEquals("http://example.org/main/doc.pdf", urls.primaryUrl)
        assertEquals("http://example.org/tn/doc_thumb.jpg", urls.tnUrl)
    }

    @Test
    fun `ressourceUrls derives the thumbnail from the primary when there is no thumbnailed bessource`() {
        givenStorage(httpStorage())
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        val urls = service.ressourceUrls(medium)

        assertEquals("http://example.org/main/doc.pdf", urls!!.primaryUrl)
        assertEquals("http://example.org/tn/doc.pdf", urls.tnUrl)
    }

    @Test
    fun `ressourceUrls ignores bessource types other than primary and thumbnail`() {
        givenStorage(httpStorage())
        val medium = medium(
            primary("doc.pdf", RessType.PRIMARY),
            bessource("doc_thumb.jpg", RessType.TN),
            // storage 2 is unknown to the repository: would fail if the pic was provided
            bessource("doc.jpg", RessType.PIC, storageId = 2)
        )

        val urls = service.ressourceUrls(medium)

        assertEquals("http://example.org/main/doc.pdf", urls!!.primaryUrl)
        assertEquals("http://example.org/tn/doc_thumb.jpg", urls.tnUrl)
    }

    @Test
    fun `ressourceUrls returns null when the storage has no in use tn location`() {
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP)))
        val medium = medium(primary("doc.pdf", RessType.PRIMARY))

        assertNull(service.ressourceUrls(medium))
    }

    @Test
    fun `ressourceUrls returns null when the storage does not exist`() {
        val medium = medium(primary("doc.pdf", RessType.PRIMARY, storageId = 9))

        assertNull(service.ressourceUrls(medium))
    }

    @Test
    fun `ressourceUrls returns null when the medium has no primary bessource`() {
        givenStorage(httpStorage())
        val medium = medium(bessource("thumb.jpg", RessType.TN))

        assertNull(service.ressourceUrls(medium))
    }

    @Test
    fun `ressourceUrls returns null for media without bessources`() {
        givenStorage(httpStorage())
        val medium = medium()

        assertNull(service.ressourceUrls(medium))
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private val storages: MutableMap<Int, Storage> = mutableMapOf()

    private fun httpStorage(id: Int = 1, extension: String? = null): Storage = storage(
        id,
        location("http://example.org/main", LocationType.MAIN_HTTP, extension = extension),
        location("http://example.org/tn", LocationType.TN_HTTP)
    )

    private fun location(
        uri: String,
        locationType: LocationType,
        inuse: Byte = 1,
        extension: String? = null
    ): Location = Location().apply {
        name = uri
        this.uri = uri
        this.locationType = locationType.i
        this.inuse = inuse
        this.extension = extension
    }

    private fun storage(id: Int, vararg locations: Location): Storage = Storage().apply {
        this.id = id
        name = "storage-$id"
        this.locations = locations.toList()
    }

    private fun givenStorage(storage: Storage) {
        // MediaService.url resolves via bessource.storage, LocationService via the repository
        whenever(storageRepository.findById(storage.id!!)).thenReturn(Optional.of(storage))
        storages[storage.id!!] = storage
    }

    private fun medium(vararg bessources: Bessource): Medium = Medium().apply {
        id = 5
        name = "medium-5"
        this.bessources = bessources.toMutableList()
    }

    private fun primary(name: String, ressType: RessType, storageId: Int = 1): Bessource =
        bessource(name, ressType, storageId)

    private fun bessource(name: String, ressType: RessType, storageId: Int = 1): Bessource =
        Bessource().apply {
            id = name.hashCode()
            this.name = name
            this.ressType = ressType.i
            this.storage = storages[storageId] ?: storage(storageId)
        }
}