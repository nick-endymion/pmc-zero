package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    /** mocked, since the scan behind an expansion is LocationService's and is tested there */
    private val locationService: LocationService = mock()

    /** the subpath the last expansion was asked to scan, which is what the subpath tests read */
    private var askedSubpath: String? = null

    @BeforeEach
    fun setUp() {
        val realLocationService = LocationService(locationRepository, StorageService(storageRepository), bessourceRepository)
        val mediaService = MediaService(mediaRepository, bessourceRepository, realLocationService)
        service = MsetService(
            mediaRepository, msetRepository, mediaService, StorageService(storageRepository), locationService
        )

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
    // The answer to an expansion
    // -------------------------------------------------------------------------------------

    @Test
    fun `answers the saved set with its id and no separate msetId`() {
        // the id is read off the mset rather than repeated beside it, so there is one place to read it
        // from and nothing that can disagree with it
        val mset = Mset().apply { id = 5 }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)
        expansionFor(1, "a.pdf")

        val result = service.expandMset(5, locationId = 1)

        assertEquals(5, result.mset.id)
    }

    @Test
    fun `answers the same shape as a draft`() {
        // one TO for both, so a caller reads a scan the same way whichever endpoint produced it
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(Mset().apply { id = 5; subpath = "tn" })
        expansionFor(1, "tn/jan.pdf")

        val result = service.expandMset(5, locationId = 1)

        assertEquals(1, result.addedFiles)
        assertEquals(0, result.knownFiles)
        assertEquals("tn", result.subpath)
        assertEquals(listOf("jan.pdf"), result.mset.media!!.map { it.name })
    }

    // -------------------------------------------------------------------------------------
    // Stepping through the sets in id order
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the set above the given one`() {
        givenNeighbour(above = 9)

        assertEquals(9, service.findAbove(5).id)
    }

    @Test
    fun `finds the set below the given one`() {
        givenNeighbour(below = 2)

        assertEquals(2, service.findBelow(5).id)
    }

    @Test
    fun `asks the repository for the set above`() {
        // the ordering itself is the query's business, which is covered by the persistence test; what
        // belongs here is that this hands the id to it unchanged
        givenNeighbour(above = 9)

        service.findAbove(5)

        verify(msetRepository).findFirstAboveId(5)
    }

    @Test
    fun `asks the repository for the set below`() {
        givenNeighbour(below = 2)

        service.findBelow(5)

        verify(msetRepository).findFirstBelowId(5)
    }

    @Test
    fun `answers the neighbour of an id no set has`() {
        // only the direction matters, so a list one row out of date still steps correctly
        givenNeighbour(above = 9)

        assertEquals(9, service.findAbove(5).id)
    }

    @Test
    fun `throws NotFoundException when there is no set above`() {
        // the end of the collection, which a caller has to be able to tell from a set it has seen
        whenever(msetRepository.findFirstAboveId(5)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.findAbove(5)
        }
    }

    @Test
    fun `throws NotFoundException when there is no set below`() {
        whenever(msetRepository.findFirstBelowId(5)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.findBelow(5)
        }
    }

    /** a set standing in for the neighbour the repository answers */
    private fun givenNeighbour(above: Int? = null, below: Int? = null) {
        whenever(msetRepository.findFirstAboveId(any())).thenReturn(above?.let { Mset().apply { id = it } })
        whenever(msetRepository.findFirstBelowId(any())).thenReturn(below?.let { Mset().apply { id = it } })
    }

    // -------------------------------------------------------------------------------------
    // Expanding a set
    // -------------------------------------------------------------------------------------

    @Test
    fun `expands the set named by its id`() {
        val mset = Mset().apply { id = 5; name = "scanned"; subpath = "tn/2020" }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)
        expansionFor(1, "tn/2020", "tn/2020/jan.pdf")

        val result = service.expandMset(5, locationId = 1)

        assertEquals(5, result.mset.id)
        assertEquals("scanned", result.mset.name)
        verify(msetRepository).save(any<Mset>())
    }

    @Test
    fun `expands a set from the subpath it records when none is given`() {
        // a set knows the directory it was built from, so a caller expanding it need not repeat it
        val mset = Mset().apply { id = 5; subpath = "tn/2020" }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)

        val expansion = expansionFor(1, "tn/2020/jan.pdf")
        service.expandMset(5, locationId = 1)

        assertEquals("tn/2020", askedSubpath)
        assertEquals(1, expansion.addedFiles)
        assertEquals(listOf("jan.pdf"), mset.media.map { it.name })
    }

    @Test
    fun `expands a set from a subpath the caller names instead`() {
        val mset = Mset().apply { id = 5; subpath = "tn/2020" }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)

        val expansion = expansionFor(1, "2021/sep.pdf")
        service.expandMset(5, locationId = 1, subdir = "2021")

        // the parameter wins, which is what makes this usable for a set whose directory moved
        assertEquals("2021", askedSubpath)
        assertEquals(1, expansion.addedFiles)
        assertEquals(listOf("sep.pdf"), mset.media.map { it.name })
    }

    @Test
    fun `expands a set from the location root when it records no subpath`() {
        // a set scanned from the root itself has no subpath, so that is what the scan looks at
        val mset = Mset().apply { id = 5 }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)

        val expansion = expansionFor(1, "a.pdf")
        service.expandMset(5, locationId = 1)

        assertNull(askedSubpath)
        assertEquals(1, expansion.addedFiles)
    }

    @Test
    fun `saves the set even when the scan added nothing`() {
        // a repeated call over an unchanged directory has to be harmless, so this is a save of the set
        // as it was rather than a refusal
        val mset = Mset().apply { id = 5; subpath = "tn/2020" }
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(mset)
        // a scan that finds nothing, which is the state of a directory that has not changed
        expansionFor(1)

        val result = service.expandMset(5, locationId = 1)

        assertEquals(0, result.addedFiles)
        verify(msetRepository).save(mset)
    }

    @Test
    fun `answers how many files were stored already`() {
        // so a caller can tell an up to date directory from a scan that found nothing at all
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(Mset().apply { id = 5 })
        whenever(locationService.expandMset(any(), eq(1), anyOrNull()))
            .thenReturn(LocationService.Scan(Mset().apply { id = 5 }, 0, 4))

        val result = service.expandMset(5, locationId = 1)

        assertEquals(4, result.knownFiles)
        assertEquals(0, result.addedFiles)
    }

    @Test
    fun `answers the subpath that was scanned`() {
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(
            Mset().apply { id = 5; subpath = "tn/2020" }
        )
        expansionFor(1, "tn/2020/jan.pdf")

        val result = service.expandMset(5, locationId = 1)

        assertEquals("tn/2020", result.subpath)
    }

    @Test
    fun `answers the set with its media so a caller sees the new ones`() {
        givenMsetIsSaved()
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(Mset().apply { id = 5 })
        expansionFor(1, "a.pdf")

        val result = service.expandMset(5, locationId = 1)

        assertEquals(listOf("a.pdf"), result.mset.media!!.map { it.name })
    }

    @Test
    fun `throws NotFoundException for an unknown mset`() {
        whenever(msetRepository.findByIdOrNullWithMedia(99)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.expandMset(99, locationId = 1)
        }
    }

    @Test
    fun `does not expand a set that has to be read with its media`() {
        // the media have to be loaded before anything is added to them, so the plain find is not enough
        whenever(msetRepository.findByIdOrNullWithMedia(5)).thenReturn(Mset().apply { id = 5 })
        givenMsetIsSaved()
        expansionFor(1)

        service.expandMset(5, locationId = 1)

        verify(msetRepository).findByIdOrNullWithMedia(5)
        verify(msetRepository, never()).findById(any())
    }

    /**
     * a stubbed scan of location [locationId] that finds [names] and adds a medium for each
     *
     * The subpath is left to whatever the call passes, so a test can read it off [askedSubpath]
     * instead of asserting on a stubbed matcher, which is what the tests about which directory is
     * scanned need. The scan itself is covered by LocationServiceTest.
     */
    private fun expansionFor(locationId: Int, vararg names: String): LocationService.Scan {
        val media = names.map { name ->
            Medium().apply {
                this.name = name.substringAfterLast('/')
                bessources = mutableListOf(
                    Bessource().apply {
                        this.name = name
                        // the response is mapped through the bessource, which reads its storage
                        storage = Storage().apply { id = 1 }
                    }
                )
            }
        }
        // answered rather than returned, so the media are added to the set the way the real service
        // adds them: the Scan carries the set it worked on, it does not put the media there itself
        whenever(locationService.expandMset(any(), eq(locationId), anyOrNull()))
            .thenAnswer { invocation ->
                askedSubpath = invocation.getArgument<String?>(2)
                val mset = invocation.getArgument<Mset>(0)
                mset.media.addAll(media)
                LocationService.Scan(mset, media.size, 0)
            }
        return LocationService.Scan(Mset().apply { id = 7 }, media.size, 0)
    }

    // -------------------------------------------------------------------------------------
    // Msets of a storage
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the msets of a storage`() {
        givenStorage(storage(1))
        whenever(msetRepository.findByStorageId(1)).thenReturn(
            listOf(Mset().apply { id = 5; name = "scanned" })
        )

        val result = service.findByStorageId(1)

        assertEquals(listOf(5), result.map { it.id })
    }

    @Test
    fun `finds no msets for a storage that holds none`() {
        givenStorage(storage(1))
        whenever(msetRepository.findByStorageId(1)).thenReturn(emptyList())

        assertTrue(service.findByStorageId(1).isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // Msets of one url
    // -------------------------------------------------------------------------------------

    /** A page may have been imported more than once, so this is a list rather than one set. */
    @Test
    fun `finds the msets of a url`() {
        whenever(msetRepository.findAllByUrlOrderById("http://example.org/g.html")).thenReturn(
            listOf(
                Mset().apply { id = 5; name = "first"; url = "http://example.org/g.html" },
                Mset().apply { id = 9; name = "second"; url = "http://example.org/g.html" }
            )
        )

        val result = service.findByUrl("http://example.org/g.html")

        assertEquals(listOf(5, 9), result.map { it.id })
    }

    /**
     * A page that was never scraped answers an empty list rather than an error: a caller asking this
     * is usually about to scrape it.
     */
    @Test
    fun `finds no msets for a url none was imported from`() {
        whenever(msetRepository.findAllByUrlOrderById("http://example.org/g.html")).thenReturn(emptyList())

        assertTrue(service.findByUrl("http://example.org/g.html").isEmpty())
    }

    /** The url is asked for as it is, since a repository matching a fragment of one is a different query. */
    @Test
    fun `asks the repository for the url as it is`() {
        service.findByUrl("http://example.org/g.html?size=large")

        verify(msetRepository).findAllByUrlOrderById("http://example.org/g.html?size=large")
    }

    @Test
    fun `checks the storage exists before looking for its msets`() {
        // an unknown id is not a storage that happens to hold nothing
        whenever(storageRepository.findById(42)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.findByStorageId(42)
        }
        verify(msetRepository, never()).findByStorageId(any())
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