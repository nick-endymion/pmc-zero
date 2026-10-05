package org.endy.pmczero.service

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [MsetService.ressourcesInUse], which resolves the urls of the media of a set
 * through [LocationService.providePhysicalRessources].
 */
class MsetServiceTest {

    private val mediaRepository: MediaRepository = mock()
    private val bessourceRepository: BessourceRepository = mock()
    private val msetRepository: MsetRepository = mock()
    private val locationRepository: LocationRepository = mock()
    private val storageRepository: StorageRepository = mock()

    private lateinit var service: MsetService

    @BeforeEach
    fun setUp() {
        val locationService = LocationService(locationRepository, StorageService(storageRepository))
        val mediaService = MediaService(mediaRepository, bessourceRepository, locationService)
        service = MsetService(mediaRepository, msetRepository, mediaService)
    }

    @Test
    fun `fills the primary and thumbnail url from the physical locations`() {
        givenStorage(httpStorage())
        givenMset(medium(name = "doc", primary = bessource("doc.pdf", RessType.PRIMARY)))

        val result = service.ressourcesInUse(1)

        assertEquals(1, result.size)
        assertEquals(10, result[0].id)
        assertEquals("doc", result[0].name)
        assertEquals("http://example.org/main/doc.pdf", result[0].primaryUrl)
        // no thumbnailed bessource: one is derived from the primary, so it carries its name
        assertEquals("http://example.org/tn/doc.pdf", result[0].tnUrl)
    }

    @Test
    fun `uses the thumbnailed bessource when the medium has one`() {
        givenStorage(httpStorage())
        givenMset(
            medium(
                name = "doc",
                primary = bessource("doc.pdf", RessType.PRIMARY),
                tn = bessource("doc_thumb.jpg", RessType.TN)
            )
        )

        val result = service.ressourcesInUse(1)

        assertEquals("http://example.org/main/doc.pdf", result[0].primaryUrl)
        assertEquals("http://example.org/tn/doc_thumb.jpg", result[0].tnUrl)
    }

    @Test
    fun `ignores bessource types other than primary and thumbnail`() {
        givenStorage(httpStorage())
        givenMset(
            medium(
                name = "doc",
                primary = bessource("doc.pdf", RessType.PRIMARY),
                tn = bessource("doc_thumb.jpg", RessType.TN),
                // storage 2 is unknown to the repository: would fail if the pic was resolved
                pic = bessource("doc.jpg", RessType.PIC, storageId = 2)
            )
        )

        val result = service.ressourcesInUse(1)

        assertEquals(1, result.size)
        assertEquals("http://example.org/main/doc.pdf", result[0].primaryUrl)
        assertEquals("http://example.org/tn/doc_thumb.jpg", result[0].tnUrl)
    }

    @Test
    fun `returns one entry per medium in order`() {
        givenStorage(httpStorage(id = 1))
        givenMset(
            medium(
                id = 10, name = "first",
                primary = bessource("first.pdf", RessType.PRIMARY, storageId = 1),
                tn = bessource("first_thumb.jpg", RessType.TN, storageId = 1)
            ),
            medium(
                id = 11, name = "second",
                primary = bessource("second.pdf", RessType.PRIMARY, storageId = 1),
                tn = bessource("second_thumb.jpg", RessType.TN, storageId = 1)
            )
        )

        val result = service.ressourcesInUse(1)

        assertEquals(listOf(10, 11), result.map { it.id })
        assertEquals(
            listOf(
                "http://example.org/main/first.pdf",
                "http://example.org/main/second.pdf"
            ),
            result.map { it.primaryUrl }
        )
        assertEquals(
            listOf(
                "http://example.org/tn/first_thumb.jpg",
                "http://example.org/tn/second_thumb.jpg"
            ),
            result.map { it.tnUrl }
        )
    }

    @Test
    fun `skips migrated media without bessources`() {
        givenStorage(httpStorage())
        givenMset(medium(id = 10, name = "legacy-folder"))

        assertTrue(service.ressourcesInUse(1).isEmpty())
    }

    @Test
    fun `skips media without a primary bessource`() {
        givenStorage(httpStorage())
        givenMset(medium(id = 10, name = "thumb-only", tn = bessource("thumb.jpg", RessType.TN)))

        assertTrue(service.ressourcesInUse(1).isEmpty())
    }

    @Test
    fun `skips media whose storage has no in use location of the required type`() {
        // no TN location, so the thumbnail cannot be provided
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP)))
        givenMset(
            medium(
                name = "doc",
                primary = bessource("doc.pdf", RessType.PRIMARY),
                tn = bessource("doc_thumb.jpg", RessType.TN)
            )
        )

        assertTrue(service.ressourcesInUse(1).isEmpty())
    }

    @Test
    fun `skips media whose storage does not exist`() {
        givenMset(medium(name = "doc", primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 9)))

        assertTrue(service.ressourcesInUse(1).isEmpty())
    }

    @Test
    fun `replaces the extension with jpg when the main location declares one`() {
        givenStorage(httpStorage(extension = "pdf"))
        givenMset(medium(name = "doc", primary = bessource("doc.pdf", RessType.PRIMARY)))

        assertEquals("http://example.org/main/doc.jpg", service.ressourcesInUse(1)[0].primaryUrl)
    }

    // -------------------------------------------------------------------------------------
    // Saving
    // -------------------------------------------------------------------------------------

    @Test
    fun `save hands the mset with its media to the repository`() {
        val mset = Mset().apply { name = "scanned" }
        givenMsetIsSaved()

        val saved = service.save(mset)

        verify(msetRepository).save(mset)
        assertSame(mset, saved)
    }

    @Test
    fun `save links every medium back to the mset it is saved into`() {
        val medium = Medium().apply { name = "a.jpg" }
        val mset = Mset().apply { name = "scanned"; media = mutableListOf(medium) }
        givenMsetIsSaved()

        service.save(mset)

        assertSame(mset, medium.mset)
    }

    @Test
    fun `save links every bessource back to its medium`() {
        val bessource = Bessource().apply { name = "tn/a.jpg"; storage = storage(1) }
        val medium = Medium().apply { name = "a.jpg"; bessources = mutableListOf(bessource) }
        val mset = Mset().apply { name = "scanned"; media = mutableListOf(medium) }
        givenMsetIsSaved()

        service.save(mset)

        assertSame(medium, bessource.medium)
    }

    // CrudRepository.save returns whatever the repository answers, a real one hands back the row
    // with its generated id, so the mock echoes its argument here
    private fun givenMsetIsSaved() {
        whenever(msetRepository.save(any<Mset>())).thenAnswer { it.getArgument(0) }
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

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
        whenever(storageRepository.findById(storage.id!!)).thenReturn(Optional.of(storage))
    }

    private fun givenMset(vararg media: Medium) {
        val mset = Mset().apply {
            id = 1
            name = "set-1"
            this.media = media.toMutableList()
        }
        whenever(msetRepository.findById(1)).thenReturn(Optional.of(mset))
    }

    private fun medium(
        id: Int = 10,
        name: String,
        primary: Bessource? = null,
        tn: Bessource? = null,
        pic: Bessource? = null
    ): Medium = Medium().apply {
        this.id = id
        this.name = name
        this.bessources = listOfNotNull(primary, tn, pic).toMutableList()
    }

    private fun bessource(name: String, ressType: RessType, storageId: Int = 1): Bessource =
        Bessource().apply {
            id = name.hashCode()
            this.name = name
            this.ressType = ressType.i
            this.storage = storage(storageId)
        }
}