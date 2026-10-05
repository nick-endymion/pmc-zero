package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.StorageRepository
import org.endy.pmczero.to.BessourceTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Optional

/**
 * Unit tests for [LocationService.providePhysicalRessources].
 *
 * Unless a test states otherwise, the bessource list passed in already contains a thumbnailed
 * (TN) bessource, so the TN generation step stays out of the way and the test can focus on one
 * behaviour. Tests around the TN generation itself pass a list with only a primary bessource.
 */
class LocationServiceTest {

    private val locationRepository: LocationRepository = mock()
    private val storageRepository: StorageRepository = mock()
    private val bessourceRepository: BessourceRepository = mock()

    private lateinit var service: LocationService

    @TempDir
    lateinit var tempDir: File

    /** Location ids for the locations registered by [givenLocation] */
    private var nextLocationId = 100

    /** Storage ids for the storages registered by [givenExistingLocation] */
    private var nextStorageId = 100

    @BeforeEach
    fun setUp() {
        // real StorageService on top of a mocked repository: keeps the (final) service class unmocked
        service = LocationService(locationRepository, StorageService(storageRepository), bessourceRepository)
    }

    // -------------------------------------------------------------------------------------
    // HTTP representation
    // -------------------------------------------------------------------------------------

    @Test
    fun `HTTP assigns MAIN_HTTP to a primary bessource without location type and builds its url`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals(LocationType.MAIN_HTTP, result[0].locationType)
        assertEquals("http://example.org/main/doc.pdf", result[0].url)
    }

    @Test
    fun `HTTP assigns TN_HTTP to a thumbnailed bessource without location type and builds its url`() {
        givenStorage(httpStorage())
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(tn), "HTTP")

        assertEquals(1, result.size)
        assertEquals(LocationType.TN_HTTP, result[0].locationType)
        assertEquals("http://example.org/tn/thumb.jpg", result[0].url)
    }

    @Test
    fun `resolves a mixed list of primary and thumbnailed bessources keeping order and per type urls`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("a_thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals(2, result.size)
        assertEquals(listOf("a.pdf", "a_thumb.jpg"), result.map { it.name })
        assertSame(primary, result[0])
        assertSame(tn, result[1])
        assertEquals(
            listOf(LocationType.MAIN_HTTP, LocationType.TN_HTTP),
            result.map { it.locationType }
        )
        assertEquals(
            listOf("http://example.org/main/a.pdf", "http://example.org/tn/a_thumb.jpg"),
            result.map { it.url }
        )
    }

    @Test
    fun `keeps an already assigned location type instead of deriving it from the resource type`() {
        givenStorage(httpStorage())
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)
        // PIC bessource: location type is given, so ressType is never inspected
        val pic = bessource("p.jpg", RessType.PIC, storageId = 1, locationType = LocationType.TN_HTTP)

        val result = service.providePhysicalRessources(listOf(tn, pic), "HTTP")

        assertEquals(LocationType.TN_HTTP, result[1].locationType)
        assertEquals("http://example.org/tn/p.jpg", result[1].url)
    }

    @Test
    fun `does not touch the location repository while providing urls`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        verifyNoInteractions(locationRepository)
    }

    // -------------------------------------------------------------------------------------
    // FILE representation
    // -------------------------------------------------------------------------------------

    @Test
    fun `FILE assigns TN_FS to a thumbnailed bessource without location type`() {
        givenStorage(fileStorage())
        val tn = bessource("thumb.png", RessType.TN, storageId = 2)

        val result = service.providePhysicalRessources(listOf(tn), "FILE")

        assertEquals(1, result.size)
        assertEquals(LocationType.TN_FS, result[0].locationType)
        assertEquals("/srv/media-tn/thumb.png", result[0].url)
    }

    @Test
    fun `FILE assigns MAIN_FS to a primary bessource without location type`() {
        givenStorage(fileStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 2)
        val tn = bessource("thumb.png", RessType.TN, storageId = 2)

        val result = service.providePhysicalRessources(listOf(primary, tn), "FILE")

        assertEquals(LocationType.MAIN_FS, result[0].locationType)
        assertEquals("/srv/media/doc.pdf", result[0].url)
    }

    @Test
    fun `FILE resolves an explicitly assigned TN_FS location`() {
        givenStorage(fileStorage())
        val tn = bessource("thumb.png", RessType.TN, storageId = 2, locationType = LocationType.TN_FS)

        val result = service.providePhysicalRessources(listOf(tn), "FILE")

        assertEquals("/srv/media-tn/thumb.png", result[0].url)
    }

    @Test
    fun `FILE resolves a primary bessource with an explicitly assigned MAIN_FS location`() {
        givenStorage(fileStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 2, locationType = LocationType.MAIN_FS)

        val result = service.providePhysicalRessources(listOf(primary), "FILE")

        // no TN in the input, so one is generated and resolved against the TN_FS location
        assertEquals(2, result.size)
        assertEquals("/srv/media/doc.pdf", result[0].url)
        assertEquals("/srv/media-tn/doc.pdf", result[1].url)
    }

    // -------------------------------------------------------------------------------------
    // Invalid input
    // -------------------------------------------------------------------------------------

    @Test
    fun `throws NotFoundException for an unknown representation type`() {
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary), "FTP")
        }
    }

    @Test
    fun `representation type is matched case sensitively`() {
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary), "http")
        }
    }

    @Test
    fun `throws NotFoundException for a resource type that has no location mapping`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val pic = bessource("p.jpg", RessType.PIC, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary, pic), "HTTP")
        }
    }

    @Test
    fun `throws NotFoundException for a null resource type without location type`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val unknown = bessource("x.bin", null, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary, unknown), "HTTP")
        }
    }

    // -------------------------------------------------------------------------------------
    // Thumbnailing
    // -------------------------------------------------------------------------------------

    @Test
    fun `does not add a second thumbnailed bessource when one is already present`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("a_thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals(2, result.size)
        assertEquals(1, result.count { it.ressType == RessType.TN.i })
    }

    @Test
    fun `appends a generated thumbnailed bessource when none is present`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary), "HTTP")

        assertEquals(2, result.size)
        assertEquals(RessType.TN.i, result[1].ressType)
        assertEquals("a.pdf", result[1].name)
        assertEquals(primary.mediumId, result[1].mediumId)
        assertEquals(1, result[1].storageId)
        assertEquals(LocationType.TN_HTTP, result[1].locationType)
        // the generated TN keeps the name of the primary, the TN location declares no extension
        assertEquals("http://example.org/tn/a.pdf", result[1].url)
    }

    @Test
    fun `appends the generated thumbnailed bessource after the input ones`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val pic = bessource("a.jpg", RessType.PIC, storageId = 1, locationType = LocationType.MAIN_HTTP)

        val result = service.providePhysicalRessources(listOf(primary, pic), "HTTP")

        assertEquals(listOf(RessType.PRIMARY.i, RessType.PIC.i, RessType.TN.i), result.map { it.ressType })
        assertEquals(
            listOf(
                "http://example.org/main/a.pdf",
                "http://example.org/main/a.jpg",
                "http://example.org/tn/a.pdf"
            ),
            result.map { it.url }
        )
    }

    @Test
    fun `generated thumbnailed bessource keeps the storage of the primary bessource`() {
        givenStorage(storage(1, location("http://storage-1/main", LocationType.MAIN_HTTP),
            location("http://storage-1/tn", LocationType.TN_HTTP)))
        givenStorage(storage(2, location("http://storage-2/main", LocationType.MAIN_HTTP),
            location("http://storage-2/tn", LocationType.TN_HTTP)))
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary), "HTTP")

        assertEquals(1, result[1].storageId)
        assertEquals("http://storage-1/tn/a.pdf", result[1].url)
    }

    // -------------------------------------------------------------------------------------
    // Storage / location lookup
    // -------------------------------------------------------------------------------------

    @Test
    fun `resolves each bessource against the location of its own storage`() {
        givenStorage(storage(1, location("http://a.example.org/main", LocationType.MAIN_HTTP),
            location("http://a.example.org/tn", LocationType.TN_HTTP)))
        givenStorage(storage(2, location("http://b.example.org/main", LocationType.MAIN_HTTP)))
        val first = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val second = bessource("b.pdf", RessType.PRIMARY, storageId = 2)
        val tn = bessource("a_thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(first, second, tn), "HTTP")

        assertEquals(
            listOf(
                "http://a.example.org/main/a.pdf",
                "http://b.example.org/main/b.pdf",
                "http://a.example.org/tn/a_thumb.jpg"
            ),
            result.map { it.url }
        )
        verify(storageRepository, times(2)).findById(1)
        verify(storageRepository, times(1)).findById(2)
    }

    @Test
    fun `throws NotFoundException when the storage of a bessource does not exist`() {
        whenever(storageRepository.findById(42)).thenReturn(Optional.empty())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 42)
        val tn = bessource("a_thumb.jpg", RessType.TN, storageId = 42)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary, tn), "HTTP")
        }
    }

    @Test
    fun `throws NotFoundException when the storage has no in use location of the required type`() {
        // storage without a TN location
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP)))
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(tn), "HTTP")
        }
    }

    @Test
    fun `ignores locations that are not in use`() {
        givenStorage(
            storage(
                1,
                location("http://example.org/old", LocationType.MAIN_HTTP, inuse = 0),
                location("http://example.org/main", LocationType.MAIN_HTTP, inuse = 1),
                location("http://example.org/tn", LocationType.TN_HTTP, inuse = 1)
            )
        )
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals("http://example.org/main/doc.pdf", result[0].url)
    }

    @Test
    fun `throws NotFoundException when the matching location is not in use`() {
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP, inuse = 0)))
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary, tn), "HTTP")
        }
    }

    @Test
    fun `throws NotFoundException when a bessource has no storage id`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = null, locationType = LocationType.MAIN_HTTP)

        assertThrows<NotFoundException> {
            service.providePhysicalRessources(listOf(primary), "HTTP")
        }
    }

    @Test
    fun `throws NoSuchElementException when there is no primary bessource to derive a thumbnail from`() {
        givenStorage(httpStorage())
        val pic = bessource("p.jpg", RessType.PIC, storageId = 1, locationType = LocationType.MAIN_HTTP)

        assertThrows<NoSuchElementException> {
            service.providePhysicalRessources(listOf(pic), "HTTP")
        }
    }

    @Test
    fun `throws NoSuchElementException for an empty list of bessources`() {
        assertThrows<NoSuchElementException> {
            service.providePhysicalRessources(emptyList(), "HTTP")
        }
    }

    // -------------------------------------------------------------------------------------
    // Url building
    // -------------------------------------------------------------------------------------

    @Test
    fun `replaces the file extension with jpg when the location declares one`() {
        givenStorage(httpStorage(extension = "pdf"))
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals("http://example.org/main/doc.jpg", result[0].url)
    }

    @Test
    fun `appends jpg when the name has no extension to strip`() {
        givenStorage(httpStorage(extension = "pdf"))
        val primary = bessource("doc", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals("http://example.org/main/doc.jpg", result[0].url)
    }

    @Test
    fun `keeps the name untouched when the location declares no extension`() {
        givenStorage(httpStorage(extension = null))
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertEquals("http://example.org/main/doc.pdf", result[0].url)
    }

    @Test
    fun `getUrlFor returns null when the storage has no matching in use location`() {
        // storage without a TN location
        givenStorage(storage(1, location("http://example.org/main", LocationType.MAIN_HTTP)))
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1, locationType = LocationType.TN_HTTP)

        assertNull(service.getUrlFor(tn))
    }

    @Test
    fun `getUrlFor returns null when the bessource has no storage id or location type`() {
        assertNull(service.getUrlFor(bessource("a.pdf", RessType.PRIMARY, storageId = null)))
        assertNull(service.getUrlFor(bessource("a.pdf", RessType.PRIMARY, storageId = 1)))
    }

    // -------------------------------------------------------------------------------------
    // Side effects
    // -------------------------------------------------------------------------------------

    @Test
    fun `mutates and returns the given bessource instances`() {
        givenStorage(httpStorage())
        val primary = bessource("doc.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("thumb.jpg", RessType.TN, storageId = 1)

        val result = service.providePhysicalRessources(listOf(primary, tn), "HTTP")

        assertSame(primary, result[0])
        assertSame(tn, result[1])
        assertEquals(LocationType.MAIN_HTTP, primary.locationType)
        assertEquals("http://example.org/main/doc.pdf", primary.url)
    }

    @Test
    fun `is idempotent when called twice with the same bessources`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val tn = bessource("a_thumb.jpg", RessType.TN, storageId = 1)
        val input = listOf(primary, tn)

        val first = service.providePhysicalRessources(input, "HTTP")
        val second = service.providePhysicalRessources(input, "HTTP")

        assertEquals(first.map { it.url }, second.map { it.url })
        assertEquals(first.map { it.locationType }, second.map { it.locationType })
        assertTrue(first.zip(second).all { (a, b) -> a === b })
    }

    @Test
    fun `does not modify the input list when a thumbnailed bessource is generated`() {
        givenStorage(httpStorage())
        val primary = bessource("a.pdf", RessType.PRIMARY, storageId = 1)
        val input = listOf(primary)

        val result = service.providePhysicalRessources(input, "HTTP")

        assertEquals(1, input.size)
        assertEquals(2, result.size)
    }

    // -------------------------------------------------------------------------------------
    // File system accessibility
    // -------------------------------------------------------------------------------------

    @Test
    fun `isFileSystemAccessible is true for an existing readable and writable FS location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertTrue(service.isFileSystemAccessible(location.id!!))
    }

    @Test
    fun `isFileSystemAccessible is false when the folder of the FS location does not exist`() {
        val existing = givenExistingLocation(LocationType.TN_FS)
        val missing = File(existing.uri, "does-not-exist")

        assertFalse(service.isFileSystemAccessible(givenLocation(missing.path, LocationType.TN_FS).id!!))
    }

    @Test
    fun `isFileSystemAccessible is false when the folder of the FS location is not readable`() {
        val existing = givenExistingLocation(LocationType.MAIN_FS)
        val folder = File(existing.uri)
        // Windows keeps the owner fully privileged, so the read permission cannot be revoked there
        assumeTrue(folder.setReadable(false) && !folder.canRead())

        assertFalse(service.isFileSystemAccessible(existing.id!!))
    }

    @Test
    fun `isFileSystemAccessible is false when the folder of the FS location is not writable`() {
        val existing = givenExistingLocation(LocationType.MAIN_FS)
        val folder = File(existing.uri)
        // Windows keeps the owner fully privileged, so the write permission cannot be revoked there
        assumeTrue(folder.setWritable(false) && !folder.canWrite())

        assertFalse(service.isFileSystemAccessible(existing.id!!))
    }

    @Test
    fun `isFileSystemAccessible only asks for an accessible path, not for a folder`() {
        val existing = givenExistingLocation(LocationType.MAIN_FS)
        val file = File(existing.uri, "doc.pdf").apply { createNewFile() }

        assertTrue(service.isFileSystemAccessible(givenLocation(file.path, LocationType.MAIN_FS).id!!))
    }

    @Test
    fun `isFileSystemAccessible is false for an HTTP location`() {
        givenLocation("http://example.org/main", LocationType.MAIN_HTTP)
        val tn = givenLocation("http://example.org/tn", LocationType.TN_HTTP)

        assertFalse(service.isFileSystemAccessible(tn.id!!))
    }

    @Test
    fun `isFileSystemAccessible is false for an FS location without uri`() {
        val location = givenLocation("http://example.org/main", LocationType.MAIN_FS).apply { uri = null }

        assertFalse(service.isFileSystemAccessible(location.id!!))
    }

    @Test
    fun `isFileSystemAccessible is false for a location without location type`() {
        val existing = givenExistingLocation(LocationType.MAIN_FS)
        existing.locationType = null

        assertFalse(service.isFileSystemAccessible(existing.id!!))
    }

    @Test
    fun `isFileSystemAccessible throws NotFoundException for an unknown location id`() {
        whenever(locationRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.isFileSystemAccessible(99)
        }
    }

    // -------------------------------------------------------------------------------------
    // Directory listing
    // -------------------------------------------------------------------------------------

    @Test
    fun `lists the direct children of the location folder`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "b.pdf").createNewFile()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "a.pdf", "b.pdf"), result.map { it.name })
    }

    @Test
    fun `marks directories in the listing`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "a.pdf").createNewFile()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(true, true, false), result.map { it.isDirectory })
    }

    @Test
    fun `lists directories before files`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "z-dir").mkdir()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "z-dir", "a.pdf"), result.map { it.name })
    }

    @Test
    fun `reports size and modification date of an entry`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        val file = File(location.uri, "a.pdf").apply { writeText("hello") }

        val entry = service.listDirectory(location.id!!)[1]

        assertEquals(5L, entry.size)
        assertEquals(file.lastModified(), entry.lastModified!!.toEpochMilli())
    }

    @Test
    fun `returns no child entries for an empty folder`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertEquals(listOf("."), service.listDirectory(location.id!!).map { it.name })
    }

    @Test
    fun `lists a subdirectory relative to the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn")

        assertEquals(listOf(".", "..", "thumb.png"), result.map { it.name })
    }

    @Test
    fun `lists a nested subdirectory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()
        File(location.uri, "a/b/c/deep.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "a/b/c")

        assertEquals(listOf(".", "..", "deep.pdf"), result.map { it.name })
    }

    @Test
    fun `parent entry of a subdirectory points at the location itself`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()

        val parent = service.listDirectory(location.id!!, subdir = "tn")[1]

        assertEquals(File(location.uri).lastModified(), parent.lastModified!!.toEpochMilli())
        assertEquals(File(location.uri).length(), parent.size)
    }

    @Test
    fun `parent entry of a nested subdirectory points at the intermediate folder`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()

        val parent = service.listDirectory(location.id!!, subdir = "a/b/c")[1]

        assertEquals(File(location.uri, "a/b").lastModified(), parent.lastModified!!.toEpochMilli())
    }

    @Test
    fun `parent entry is marked as a directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a-folder").mkdir()
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn")

        assertEquals("..", result[1].name)
        assertTrue(result[1].isDirectory)
    }

    @Test
    fun `does not add a parent entry when the location itself is listed`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "a.pdf"), result.map { it.name })
    }

    @Test
    fun `does not add a parent entry when the subdirectory resolves to the location itself`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        assertEquals(listOf(".", "a.pdf"), service.listDirectory(location.id!!, subdir = ".").map { it.name })
        assertEquals(listOf(".", "a.pdf"), service.listDirectory(location.id!!, subdir = "tn/..").map { it.name })
    }

    @Test
    fun `treats a blank subdirectory as the location itself`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "  ")

        assertEquals(listOf(".", "a.pdf"), result.map { it.name })
    }

    @Test
    fun `does not descend into subdirectories by default`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "tn"), result.map { it.name })
    }

    @Test
    fun `recursive lists the whole tree below the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/thumb.png").createNewFile()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, recursive = true)

        assertEquals(listOf(".", "tn", "tn/2020", "a.pdf", "tn/2020/jan.pdf", "tn/thumb.png"), result.map { it.name })
    }

    @Test
    fun `recursive lists the whole tree below the subdirectory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn", recursive = true)

        assertEquals(listOf(".", "..", "2020", "thumb.png"), result.map { it.name })
    }

    // -------------------------------------------------------------------------------------
    // Entry for the listed directory itself
    // -------------------------------------------------------------------------------------

    @Test
    fun `adds an entry for the listed directory itself`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()

        val result = service.listDirectory(location.id!!, subdir = "tn")

        assertEquals(".", result.first().name)
    }

    @Test
    fun `entry for the listed directory itself is marked as a directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()

        assertTrue(service.listDirectory(location.id!!, subdir = "tn").first().isDirectory)
    }

    @Test
    fun `entry for the listed directory itself reports the size and date of that directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()
        val tn = File(location.uri, "tn")

        val self = service.listDirectory(location.id!!, subdir = "tn").first()

        assertEquals(tn.length(), self.size)
        assertEquals(tn.lastModified(), self.lastModified!!.toEpochMilli())
    }

    @Test
    fun `entry for the listed directory itself is added even for the location itself`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "a.pdf"), result.map { it.name })
        assertEquals("", result.first().path)
    }

    @Test
    fun `entry for the listed directory itself is added for a recursive listing`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn", recursive = true)

        assertEquals(".", result.first().name)
        assertEquals("tn", result.first().path)
    }

    @Test
    fun `entry for the listed directory itself comes before the parent entry`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()
        File(location.uri, "a/b/c/deep.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "a/b/c")

        assertEquals(listOf(".", "..", "deep.pdf"), result.map { it.name })
    }

    @Test
    fun `path of the entry for the listed directory itself is relative to the location root`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()
        File(location.uri, "a/b/c/deep.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "a/b/c")

        assertEquals("a/b/c", result.first().path)
    }

    // -------------------------------------------------------------------------------------
    // Path relative to the location root
    // -------------------------------------------------------------------------------------

    @Test
    fun `path of an entry of the location itself equals its name`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "tn").mkdir()

        val result = service.listDirectory(location.id!!)

        // the first entry is the location itself, for which path and name both are empty
        assertEquals(listOf("", "tn", "a.pdf"), result.map { it.path })
    }

    @Test
    fun `path of an entry of a subdirectory is relative to the location root`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn")

        assertEquals(listOf("tn", "", "tn/thumb.png"), result.map { it.path })
        // the name stays relative to the listed directory
        assertEquals(listOf(".", "..", "thumb.png"), result.map { it.name })
    }

    @Test
    fun `path of a nested entry keeps the whole path below the location root`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()
        File(location.uri, "a/b/c/deep.pdf").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "a/b/c")

        assertEquals(listOf("a/b/c", "a/b", "a/b/c/deep.pdf"), result.map { it.path })
    }

    @Test
    fun `path of a recursive listing of the location equals its name`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, recursive = true)

        // the '.' entry of the location itself is the only one where the two differ
        assertEquals(listOf(".", "tn", "tn/2020", "tn/thumb.png"), result.map { it.name })
        assertEquals(listOf("", "tn", "tn/2020", "tn/thumb.png"), result.map { it.path })
    }

    @Test
    fun `path of a recursive listing of a subdirectory is relative to the location root`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/thumb.png").createNewFile()

        val result = service.listDirectory(location.id!!, subdir = "tn", recursive = true)

        assertEquals(listOf("tn", "", "tn/2020", "tn/thumb.png"), result.map { it.path })
        assertEquals(listOf(".", "..", "2020", "thumb.png"), result.map { it.name })
    }

    @Test
    fun `path of the parent entry of a top level subdirectory is the location root`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()

        assertEquals("", service.listDirectory(location.id!!, subdir = "tn")[1].path)
    }

    @Test
    fun `path of the parent entry points at the intermediate folder`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a/b/c").mkdirs()

        assertEquals("a/b", service.listDirectory(location.id!!, subdir = "a/b/c")[1].path)
    }

    @Test
    fun `listDirectory throws NotAccessibleException for an HTTP location`() {
        val location = givenLocation("http://example.org/main", LocationType.MAIN_HTTP)

        assertThrows<NotAccessibleException> {
            service.listDirectory(location.id!!)
        }
    }

    @Test
    fun `listDirectory throws NotAccessibleException when the folder is missing`() {
        val location = givenExistingLocation(LocationType.MAIN_FS).apply {
            uri = File(uri, "gone").path
        }

        assertThrows<NotAccessibleException> {
            service.listDirectory(location.id!!)
        }
    }

    @Test
    fun `listDirectory throws NotFoundException when the subdirectory is missing`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertThrows<NotFoundException> {
            service.listDirectory(location.id!!, subdir = "no-such-dir")
        }
    }

    @Test
    fun `listDirectory throws NotFoundException when the subdirectory is a file`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        assertThrows<NotFoundException> {
            service.listDirectory(location.id!!, subdir = "a.pdf")
        }
    }

    @Test
    fun `listDirectory throws NotFoundException when the subdirectory escapes the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        val outside = File(tempDir, "outside").apply { mkdir() }
        File(outside, "secret.pdf").createNewFile()

        assertThrows<NotFoundException> {
            service.listDirectory(location.id!!, subdir = "../outside")
        }
    }

    @Test
    fun `listDirectory throws NotFoundException when the subdirectory climbs out and back in`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "tn/thumb.png").createNewFile()

        assertThrows<NotFoundException> {
            service.listDirectory(location.id!!, subdir = "tn/../..")
        }
    }

    @Test
    fun `listDirectory throws NotFoundException for an unknown location id`() {
        whenever(locationRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.listDirectory(99)
        }
    }

    // -------------------------------------------------------------------------------------
    // Already known entries
    // -------------------------------------------------------------------------------------

    @Test
    fun `flags a file whose medium is stored already`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        givenExistingMedia(location, "a.pdf")

        val result = service.listDirectory(location.id!!)

        // the '.' entry is the location itself and can never be a known medium
        assertEquals(listOf(".", "a.pdf"), result.map { it.name })
        assertEquals(listOf(false, true), result.map { it.existsAlready })
    }

    @Test
    fun `leaves a file the database knows nothing about unflagged`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "b.pdf").createNewFile()
        givenExistingMedia(location, "a.pdf")

        val result = service.listDirectory(location.id!!)

        // only the file the database knows is flagged, b.pdf is not
        assertEquals(listOf("a.pdf"), result.filter { it.existsAlready }.map { it.name })
    }

    @Test
    fun `matches a bessource of a recursive listing by its path below the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020/jan.pdf").apply { parentFile.mkdirs(); createNewFile() }
        givenExistingMedia(location, "tn/2020/jan.pdf")

        val jan = service.listDirectory(location.id!!, subdir = "tn/2020", recursive = true)
            .single { it.name == "jan.pdf" }

        assertTrue(jan.existsAlready)
        // the query is answered with the path below the location, not with the entry name
        verify(bessourceRepository).findNamesOfExistingMedia(
            location.storage.id!!, RessType.PRIMARY.i, listOf("tn/2020/jan.pdf")
        )
    }

    @Test
    fun `asks the database only about the files of the listing, in no particular order`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "b.pdf").createNewFile()

        service.listDirectory(location.id!!)

        // the directories and the '.' entry are no media, so their paths are not looked up
        val asked = argumentCaptor<Collection<String>>()
        verify(bessourceRepository).findNamesOfExistingMedia(
            eq(location.storage.id!!), eq(RessType.PRIMARY.i), asked.capture()
        )
        assertEquals(setOf("a.pdf", "b.pdf"), asked.firstValue.toSet())
    }

    @Test
    fun `never flags a directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()
        File(location.uri, "a.pdf").createNewFile()
        // a bessource named like the folder would still not make the folder a known medium
        givenExistingMedia(location, "tn")

        val result = service.listDirectory(location.id!!)

        assertEquals(listOf(".", "tn"), result.filter { it.isDirectory }.map { it.name })
        assertTrue(result.none { it.isDirectory && it.existsAlready })
    }

    @Test
    fun `never flags the dot entries`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn").mkdir()

        assertTrue(service.listDirectory(location.id!!, subdir = "tn").none { it.existsAlready })
    }

    @Test
    fun `checks the whole listing in a single query`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "b.pdf").createNewFile()
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        service.listDirectory(location.id!!, recursive = true)

        verify(bessourceRepository, times(1)).findNamesOfExistingMedia(
            eq(location.storage.id!!), eq(RessType.PRIMARY.i), any()
        )
    }

    @Test
    fun `does not ask the database when the location has no storage`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        val withoutStorage = givenLocation(location.uri!!, LocationType.MAIN_FS)

        val result = service.listDirectory(withoutStorage.id!!)

        assertFalse(result.first { it.name == "a.pdf" }.existsAlready)
        verifyNoInteractions(bessourceRepository)
    }

    @Test
    fun `does not ask the database for an empty folder`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        service.listDirectory(location.id!!)

        // the location folder itself is a directory, so no file name could be looked up
        verifyNoInteractions(bessourceRepository)
    }

    // -------------------------------------------------------------------------------------
    // Draft mset
    // -------------------------------------------------------------------------------------

    @Test
    fun `draftMset creates one medium per file below the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()
        File(location.uri, "b.pdf").createNewFile()

        val mset = service.draftMset(location.id!!)

        assertEquals(listOf("a.pdf", "b.pdf"), mset.media.map { it.name })
    }

    @Test
    fun `draftMset names the mset after the scanned subpath`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        val mset = service.draftMset(location.id!!, subdir = "tn/2020")

        assertEquals("tn/2020", mset.name)
    }

    @Test
    fun `draftMset names the mset after the location when no subpath is given`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertEquals(location.name, service.draftMset(location.id!!).name)
        assertEquals(location.name, service.draftMset(location.id!!, subdir = "  ").name)
    }

    @Test
    fun `draftMset leaves out directories and the dot entries`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/thumb.png").createNewFile()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        val mset = service.draftMset(location.id!!)

        // 'tn' and 'tn/2020' are directories, '.' is the location itself
        assertEquals(listOf("jan.pdf", "thumb.png"), mset.media.map { it.name })
    }

    @Test
    fun `draftMset names a medium after the file alone, without the path below the scanned directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        val mset = service.draftMset(location.id!!, subdir = "tn/2020")

        assertEquals(listOf("jan.pdf"), mset.media.map { it.name })
    }

    @Test
    fun `draftMset names the media of equally named files in different subdirectories alike`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a").mkdirs()
        File(location.uri, "b").mkdirs()
        File(location.uri, "a/shot.jpg").createNewFile()
        File(location.uri, "b/shot.jpg").createNewFile()

        val mset = service.draftMset(location.id!!)

        assertEquals(listOf("shot.jpg", "shot.jpg"), mset.media.map { it.name })
        // the bessources keep them apart, they are the ones the url is built from
        assertEquals(
            listOf("a/shot.jpg", "b/shot.jpg"),
            mset.media.flatMap { it.bessources }.map { it.name })
    }

    @Test
    fun `draftMset names a bessource relative to the location so its url resolves`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/2020/jan.pdf").createNewFile()

        val mset = service.draftMset(location.id!!, subdir = "tn/2020")

        assertEquals(listOf("tn/2020/jan.pdf"), mset.media.flatMap { it.bessources }.map { it.name })
    }

    @Test
    fun `draftMset creates one primary bessource per medium`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val bessources = service.draftMset(location.id!!).media.flatMap { it.bessources }

        assertEquals(listOf(RessType.PRIMARY.i), bessources.map { it.ressType })
    }

    @Test
    fun `draftMset points each bessource at the storage of the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val bessource = service.draftMset(location.id!!).media.flatMap { it.bessources }[0]

        assertSame(location.storage, bessource.storage)
    }

    @Test
    fun `draftMset links each bessource back to its medium and each medium to the mset`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val mset = service.draftMset(location.id!!)
        val medium = mset.media[0]

        assertSame(mset, medium.mset)
        assertSame(medium, medium.bessources[0].medium)
    }

    @Test
    fun `draftMset derives the medium type from the file extension`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.jpg").createNewFile()
        File(location.uri, "b.mp4").createNewFile()
        File(location.uri, "c.epub").createNewFile()
        File(location.uri, "d.txt").createNewFile()

        val mset = service.draftMset(location.id!!)

        assertEquals(
            listOf(Mtype.PHOTO.i, Mtype.MOVIE.i, Mtype.BOOK.i, Mtype.UNDEFINED.i),
            mset.media.map { it.mtype })
    }

    @Test
    fun `draftMset maps to the same response shape as the media of a saved mset`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "tn/2020").mkdirs()
        File(location.uri, "tn/2020/jan.jpg").createNewFile()

        val response = service.draftMset(location.id!!, subdir = "tn").toTOwithMedia(true)

        assertEquals("tn", response.name)
        assertNull(response.id)
        // the medium carries the file name alone, the bessource the path below the location
        assertEquals(listOf("jan.jpg"), response.media!!.map { it.name })
        assertEquals(listOf(Mtype.PHOTO.i), response.media!!.map { it.mtype })
        // the medium is not nested back into the mset, like in the response of a saved mset
        assertNull(response.media!![0].mset)
        val bessource = response.media!![0].bessources[0]
        assertEquals("tn/2020/jan.jpg", bessource.name)
        assertEquals(RessType.PRIMARY.i, bessource.ressType)
        assertEquals(location.storage.id, bessource.storageId)
        // the medium is unsaved, so it has no id to hand out yet
        assertNull(bessource.mediumId)
    }

    @Test
    fun `draftMset returns unsaved entities, the draft carries no id yet`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(location.uri, "a.pdf").createNewFile()

        val mset = service.draftMset(location.id!!)

        assertNull(mset.id)
        assertNull(mset.media[0].id)
        assertNull(mset.media[0].bessources[0].id)
        assertNull(mset.created_at)
    }

    @Test
    fun `draftMset creates no media for an empty directory`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertTrue(service.draftMset(location.id!!).media.isEmpty())
    }

    @Test
    fun `draftMset throws NotAccessibleException for an HTTP location`() {
        val location = givenLocation("http://example.org/main", LocationType.MAIN_HTTP)

        assertThrows<NotAccessibleException> {
            service.draftMset(location.id!!)
        }
    }

    @Test
    fun `draftMset throws NotAccessibleException when the location has no storage`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        // storage is lateinit, so a location without one is created by simply not setting it
        val withoutStorage = givenLocation(location.uri!!, LocationType.MAIN_FS)

        assertThrows<NotAccessibleException> {
            service.draftMset(withoutStorage.id!!)
        }
    }

    @Test
    fun `draftMset throws NotFoundException when the subdirectory is missing`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)

        assertThrows<NotFoundException> {
            service.draftMset(location.id!!, subdir = "no-such-dir")
        }
    }

    @Test
    fun `draftMset throws NotFoundException when the subdirectory escapes the location`() {
        val location = givenExistingLocation(LocationType.MAIN_FS)
        File(tempDir, "outside").mkdir()

        assertThrows<NotFoundException> {
            service.draftMset(location.id!!, subdir = "../outside")
        }
    }

    @Test
    fun `draftMset throws NotFoundException for an unknown location id`() {
        whenever(locationRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.draftMset(99)
        }
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun httpStorage(id: Int = 1, extension: String? = null): Storage = storage(
        id,
        location("http://example.org/main", LocationType.MAIN_HTTP, extension = extension),
        location("http://example.org/tn", LocationType.TN_HTTP)
    )

    private fun fileStorage(id: Int = 2): Storage = storage(
        id,
        location("/srv/media", LocationType.MAIN_FS),
        location("/srv/media-tn", LocationType.TN_FS)
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

    // StorageService.findById goes through CrudRepository.findById, so the Optional is stubbed here
    private fun givenStorage(storage: Storage) {
        whenever(storageRepository.findById(storage.id!!)).thenReturn(Optional.of(storage))
    }

    // findById goes through CrudRepository.findById as well, so the Optional is stubbed here too
    private fun givenLocation(uri: String, locationType: LocationType): Location =
        Location().apply {
            id = nextLocationId++
            name = uri
            this.uri = uri
            this.locationType = locationType.i
            inuse = 1
        }.also { whenever(locationRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }

    /** creates a folder inside [tempDir] and registers a location pointing at it */
    private fun givenExistingLocation(locationType: LocationType): Location {
        val dir = File(tempDir, "location-$nextLocationId").apply { mkdir() }
        return givenLocation(dir.absolutePath, locationType)
            // a bessource always needs a storage, so the location gets one of its own
            .also { it.storage = storage(nextStorageId++) }
    }

    /**
     * a stored primary bessource named [name] in the storage of [location], which is what makes a
     * listed file of that path known already. The answer is filtered the way the query filters, so
     * a test can register several names of different storages at once.
     */
    private fun givenExistingMedia(location: Location, vararg names: String) {
        val storageId = location.storage.id!!
        whenever(
            bessourceRepository.findNamesOfExistingMedia(
                eq(storageId), eq(RessType.PRIMARY.i), any<Collection<String>>()
            )
        ).thenAnswer { invocation ->
            val asked = invocation.getArgument<Collection<String>>(2)
            names.filter { it in asked }
        }
    }

    private fun bessource(
        name: String,
        ressType: RessType?,
        storageId: Int?,
        locationType: LocationType? = null
    ): BessourceTO = BessourceTO(
        name = name,
        ressType = ressType?.i,
        mediumId = 7,
        storageId = storageId,
        locationType = locationType
    )
}