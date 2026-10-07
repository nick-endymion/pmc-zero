package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.StorageRepository
import org.springframework.data.repository.findByIdOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.Optional
import javax.persistence.EntityManager

/**
 * Unit tests for [MediaDeletionService.deleteMarkedMedium].
 *
 * Real files are written into a temp directory and a real [StorageService] over a mocked repository
 * backs the storage lookup, because the behaviour under test is a file on disk being moved: the path
 * it ends up at is the whole point, and a mock could only assert which calls were made.
 */
class MediaDeletionServiceTest {

    private val mediaRepository: MediaRepository = mock()
    private val storageRepository: StorageRepository = mock()
    private val entityManager: EntityManager = mock()

    private lateinit var service: MediaDeletionService

    @TempDir
    lateinit var tempDir: File

    /** The MAIN_FS and TN_FS folders of the storage, created up front. */
    private lateinit var mainFs: File
    private lateinit var tnFs: File

    private val storageId = 5

    @BeforeEach
    fun setUp() {
        service = MediaDeletionService(
            mediaRepository, StorageService(storageRepository), entityManager
        )
        mainFs = File(tempDir, "main").apply { mkdirs() }
        tnFs = File(tempDir, "tn").apply { mkdirs() }
        givenStorage()
    }

    // -------------------------------------------------------------------------------------
    // The mark is required
    // -------------------------------------------------------------------------------------

    @Test
    fun `refuses a medium that is not marked deleted`() {
        val medium = givenMarkedMedium(deleted = false)

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
        assertTrue(medium.bessources.isNotEmpty())
    }

    @Test
    fun `deletes nothing at all when the medium is not marked`() {
        givenMarkedMedium(deleted = false)

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
        verify(mediaRepository, never()).delete(any<Medium>())
    }

    @Test
    fun `treats a medium whose flag was never set as not marked`() {
        // the column is nullable, so a row migrated before it existed carries no value at all
        val medium = givenMarkedMedium(deleted = null)

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
    }

    @Test
    fun `throws NotFoundException for an unknown medium`() {
        whenever(mediaRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.deleteMarkedMedium(99)
        }
    }

    // -------------------------------------------------------------------------------------
    // The record
    // -------------------------------------------------------------------------------------

    @Test
    fun `deletes the medium row`() {
        val medium = givenMarkedMedium()

        service.deleteMarkedMedium(10)

        verify(mediaRepository).delete(medium)
    }

    @Test
    fun `deletes the row before the files are moved`() {
        // the move is not transactional: if it happened first and the delete failed afterwards, the
        // files would sit below DELETED for a record that is still there
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }
        // whether the file was still at its old place at the moment the row was deleted, which is what
        // makes the order observable: a move that came first would have already shifted it
        val existedAtDelete = mutableListOf<Boolean>()
        doAnswer { existedAtDelete.add(file.exists()) }
            .`when`(mediaRepository).delete(any<Medium>())

        service.deleteMarkedMedium(10)

        assertEquals(1, existedAtDelete.size)
        assertTrue(existedAtDelete.single())
        assertFalse(file.exists())
    }

    @Test
    fun `flushes so the delete has reached the database before the files move`() {
        givenMarkedMedium()

        service.deleteMarkedMedium(10)

        verify(entityManager).flush()
    }

    // -------------------------------------------------------------------------------------
    // The files
    // -------------------------------------------------------------------------------------

    @Test
    fun `moves the primary file below the DELETED folder`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }

        val result = service.deleteMarkedMedium(10)

        assertEquals(1, result.movedFiles)
        assertFalse(file.exists())
        assertTrue(File(mainFs, "DELETED/shot.jpg").isFile)
    }

    @Test
    fun `moves the thumbnail below the DELETED folder of the TN location`() {
        // the folder is per location, so the thumbnail goes below the one of the location that holds it
        givenMarkedMedium(primaryName = "shot.jpg", tnName = "shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()
        File(tnFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(2, result.movedFiles)
        assertTrue(File(tnFs, "DELETED/shot.jpg").isFile)
        assertTrue(File(mainFs, "DELETED/shot.jpg").isFile)
    }

    @Test
    fun `keeps the whole path the file was stored under`() {
        givenMarkedMedium(primaryName = "imagegap4/abc/984580928.jpg")
        File(mainFs, "imagegap4/abc").mkdirs()
        File(mainFs, "imagegap4/abc/984580928.jpg").createNewFile()

        service.deleteMarkedMedium(10)

        // the path is reproduced below the folder rather than flattened, so two files of one name in
        // two folders do not land on top of each other
        assertTrue(File(mainFs, "DELETED/imagegap4/abc/984580928.jpg").isFile)
    }

    @Test
    fun `creates the folders the path below the location needs`() {
        givenMarkedMedium(primaryName = "2020/august/jan.pdf")
        File(mainFs, "2020/august").mkdirs()
        File(mainFs, "2020/august/jan.pdf").createNewFile()

        val result = service.deleteMarkedMedium(10)

        // neither DELETED nor the folders below it exist before the move
        assertEquals(1, result.movedFiles)
        assertTrue(File(mainFs, "DELETED/2020/august/jan.pdf").isFile)
    }

    @Test
    fun `moves a file without an extension`() {
        givenMarkedMedium(primaryName = "README")
        File(mainFs, "README").createNewFile()

        service.deleteMarkedMedium(10)

        assertTrue(File(mainFs, "DELETED/README").isFile)
    }

    @Test
    fun `reads a bessource name written with backslashes`() {
        givenMarkedMedium(primaryName = "imagegap4\\abc\\984580928.jpg")
        File(mainFs, "imagegap4/abc").mkdirs()
        File(mainFs, "imagegap4/abc/984580928.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        // one path on every platform: a name written on windows must not become a single directory
        // named after a backslash
        assertEquals(1, result.movedFiles)
        assertTrue(File(mainFs, "DELETED/imagegap4/abc/984580928.jpg").isFile)
    }

    @Test
    fun `does not move a file that already sits below the DELETED folder`() {
        // the counterpart of the old prefix check: a second pass must not produce DELETED/DELETED/...
        givenMarkedMedium(primaryName = "DELETED/shot.jpg")
        File(mainFs, "DELETED").mkdirs()
        val file = File(mainFs, "DELETED/shot.jpg").apply { createNewFile() }

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.movedFiles)
        assertTrue(file.isFile)
        assertFalse(File(mainFs, "DELETED/DELETED/shot.jpg").exists())
    }

    @Test
    fun `deletes the record even when no file could be moved`() {
        val medium = givenMarkedMedium(primaryName = null)

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.movedFiles)
        verify(mediaRepository).delete(medium)
    }

    @Test
    fun `deletes the record when the file behind the bessource is gone`() {
        givenMarkedMedium(primaryName = "gone.jpg")

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.movedFiles)
        // counted, not dropped: the caller can see the bessource pointed at nothing
        assertEquals(1, result.skippedFiles)
    }

    @Test
    fun `reports how many files it left alone`() {
        // the primary points at a file that is there, the tn at one that is not
        givenMarkedMedium(primaryName = "shot.jpg", tnName = "gone.jpg")
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(1, result.movedFiles)
        assertEquals(1, result.skippedFiles)
    }

    // -------------------------------------------------------------------------------------
    // Collisions
    // -------------------------------------------------------------------------------------

    @Test
    fun `refuses to delete when a file is already there at the destination`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }
        File(mainFs, "DELETED").mkdirs()
        val taken = File(mainFs, "DELETED/shot.jpg").apply { createNewFile() }

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
        // nothing is lost: neither file is gone
        assertTrue(file.isFile)
        assertTrue(taken.isFile)
    }

    @Test
    fun `deletes nothing at all when one file collides`() {
        givenMarkedMedium(primaryName = "shot.jpg", tnName = "shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()
        File(tnFs, "shot.jpg").createNewFile()
        File(tnFs, "DELETED").mkdirs()
        File(tnFs, "DELETED/shot.jpg").createNewFile()

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
        // the primary would have been free to move, but a half deleted medium is worse
        verify(mediaRepository, never()).delete(any<Medium>())
        assertTrue(File(mainFs, "shot.jpg").isFile)
    }

    @Test
    fun `names the path that collided in the error`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()
        File(mainFs, "DELETED").mkdirs()
        File(mainFs, "DELETED/shot.jpg").createNewFile()

        val e = assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }

        // relative to the location rather than absolute, which would say nothing to a caller that does
        // not know where the storage sits
        assertTrue(e.message!!.contains("DELETED/shot.jpg"), e.message)
    }

    // -------------------------------------------------------------------------------------
    // Files that are left alone on purpose
    // -------------------------------------------------------------------------------------

    @Test
    fun `does not move a bessource that is not on the file system`() {
        // a url bessource names something that cannot be moved on disk
        val medium = givenMarkedMedium(primaryName = "shot.jpg", picName = "http://example.org/shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        // only the primary moved, the url bessource was left alone
        assertEquals(1, result.movedFiles)
        assertEquals(1, result.skippedFiles)
        assertEquals(2, medium.bessources.size)
    }

    @Test
    fun `does not move outside the location`() {
        // a name that escapes the folder must not reach the file system at all
        givenMarkedMedium(primaryName = "../outside.jpg")
        val outside = File(tempDir, "outside.jpg").apply { createNewFile() }

        service.deleteMarkedMedium(10)

        assertTrue(outside.isFile)
    }

    @Test
    fun `does not move when the storage has no FS location`() {
        val medium = givenMarkedMedium(primaryName = "shot.jpg", withoutFsLocations = true)
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.movedFiles)
        verify(mediaRepository).delete(medium)
    }

    // -------------------------------------------------------------------------------------
    // The answer
    // -------------------------------------------------------------------------------------

    @Test
    fun `answers which medium was deleted`() {
        val medium = givenMarkedMedium()

        val result = service.deleteMarkedMedium(10)

        assertEquals(10, result.mediumId)
        assertEquals(medium.name, result.name)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a storage with a MAIN_FS and a TN_FS location pointing into the temp dir */
    private fun givenStorage(withoutFsLocations: Boolean = false) {
        val storage = Storage().apply { id = storageId; name = "shelf" }
        storage.locations = if (withoutFsLocations) {
            listOf(
                location("http://example.org/main", LocationType.MAIN_HTTP),
                location("http://example.org/tn", LocationType.TN_HTTP)
            )
        } else {
            listOf(
                location(mainFs.path, LocationType.MAIN_FS),
                location(tnFs.path, LocationType.TN_FS)
            )
        }
        whenever(storageRepository.findByIdOrNullWithLocations(storageId)).thenReturn(storage)
    }

    private fun location(uri: String, locationType: LocationType): Location = Location().apply {
        name = uri
        this.uri = uri
        this.locationType = locationType.i
        inuse = 1
    }

    /**
     * a stored medium the repository knows by id, marked deleted unless told otherwise, with a
     * primary bessource and optionally a TN and a PIC one
     */
    private fun givenMarkedMedium(
        deleted: Boolean? = true,
        primaryName: String? = "shot.jpg",
        tnName: String? = null,
        picName: String? = null,
        withoutFsLocations: Boolean = false
    ): Medium {
        if (withoutFsLocations) givenStorage(withoutFsLocations = true)

        val storage = Storage().apply { id = storageId; name = "shelf" }
        val medium = Medium().apply {
            id = 10
            name = "shot"
            this.deleted = deleted
        }
        medium.bessources = listOfNotNull(
            primaryName?.let { bessource(it, RessType.PRIMARY) },
            tnName?.let { bessource(it, RessType.TN) },
            // a PIC bessource resolves to an HTTP location, so its file cannot be renamed
            picName?.let { bessource(it, RessType.PIC) }
        ).toMutableList()

        whenever(mediaRepository.findById(10)).thenReturn(Optional.of(medium))
        return medium
    }

    private fun bessource(name: String, ressType: RessType): Bessource = Bessource().apply {
        this.name = name
        this.ressType = ressType.i
        this.storage = Storage().apply { id = storageId }
    }
}