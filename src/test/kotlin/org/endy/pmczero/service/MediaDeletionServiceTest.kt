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
 * backs the storage lookup, because the behaviour under test is a file on disk being renamed: the
 * name it ends up with is the whole point, and a mock could only assert which calls were made.
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
    fun `deletes the row before the files are renamed`() {
        // the rename is not transactional: if it happened first and the delete failed afterwards,
        // the files would carry the prefix for a record that is still there
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }
        // whether the file was still under its old name at the moment the row was deleted, which is what
        // makes the order observable: a rename that came first would have already moved it
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
    fun `renames the primary file with deleted_ in front`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }

        val result = service.deleteMarkedMedium(10)

        assertEquals(1, result.renamedFiles)
        assertFalse(file.exists())
        assertTrue(File(mainFs, "deleted_shot.jpg").isFile)
    }

    @Test
    fun `renames the thumbnail in the TN location`() {
        givenMarkedMedium(primaryName = "shot.jpg", tnName = "shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()
        File(tnFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(2, result.renamedFiles)
        assertTrue(File(tnFs, "deleted_shot.jpg").isFile)
    }

    @Test
    fun `keeps the subdirectory the file sits in`() {
        givenMarkedMedium(primaryName = "2020/jan.pdf")
        File(mainFs, "2020").mkdirs()
        File(mainFs, "2020/jan.pdf").createNewFile()

        service.deleteMarkedMedium(10)

        // the prefix belongs to the file, not to the directory it lives in
        assertTrue(File(mainFs, "2020/deleted_jan.pdf").isFile)
    }

    @Test
    fun `keeps the extension so the file still opens`() {
        givenMarkedMedium(primaryName = "movie.mkv")
        File(mainFs, "movie.mkv").createNewFile()

        service.deleteMarkedMedium(10)

        assertTrue(File(mainFs, "deleted_movie.mkv").isFile)
    }

    @Test
    fun `handles a file name without an extension`() {
        givenMarkedMedium(primaryName = "README")
        File(mainFs, "README").createNewFile()

        service.deleteMarkedMedium(10)

        assertTrue(File(mainFs, "deleted_README").isFile)
    }

    @Test
    fun `does not prefix a file twice`() {
        givenMarkedMedium(primaryName = "deleted_shot.jpg")
        val file = File(mainFs, "deleted_shot.jpg").apply { createNewFile() }

        val result = service.deleteMarkedMedium(10)

        // a second pass must not grow the name further
        assertEquals(0, result.renamedFiles)
        assertTrue(file.isFile)
    }

    @Test
    fun `deletes the record even when no file could be renamed`() {
        val medium = givenMarkedMedium(primaryName = null)

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.renamedFiles)
        verify(mediaRepository).delete(medium)
    }

    @Test
    fun `deletes the record when the file behind the bessource is gone`() {
        givenMarkedMedium(primaryName = "gone.jpg")

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.renamedFiles)
        // counted, not dropped: the caller can see the bessource pointed at nothing
        assertEquals(1, result.skippedFiles)
    }

    @Test
    fun `reports how many files it left alone`() {
        // the primary points at a file that is there, the tn at one that is not
        givenMarkedMedium(primaryName = "shot.jpg", tnName = "gone.jpg")
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(1, result.renamedFiles)
        assertEquals(1, result.skippedFiles)
    }

    // -------------------------------------------------------------------------------------
    // Collisions
    // -------------------------------------------------------------------------------------

    @Test
    fun `refuses to delete when a file with the new name is already there`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        val file = File(mainFs, "shot.jpg").apply { createNewFile() }
        val taken = File(mainFs, "deleted_shot.jpg").apply { createNewFile() }

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
        File(tnFs, "deleted_shot.jpg").createNewFile()

        assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }
        // the primary would have been free to move, but a half deleted medium is worse
        verify(mediaRepository, never()).delete(any<Medium>())
        assertTrue(File(mainFs, "shot.jpg").isFile)
    }

    @Test
    fun `names the file that collided in the error`() {
        givenMarkedMedium(primaryName = "shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()
        File(mainFs, "deleted_shot.jpg").createNewFile()

        val e = assertThrows<NotAccessibleException> {
            service.deleteMarkedMedium(10)
        }

        assertTrue(e.message!!.contains("deleted_shot.jpg"), e.message)
    }

    // -------------------------------------------------------------------------------------
    // Files that are left alone on purpose
    // -------------------------------------------------------------------------------------

    @Test
    fun `does not rename a bessource that is not on the file system`() {
        // a url bessource names something that cannot be renamed on disk
        val medium = givenMarkedMedium(primaryName = "shot.jpg", picName = "http://example.org/shot.jpg")
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        // only the primary moved, the url bessource was left alone
        assertEquals(1, result.renamedFiles)
        assertEquals(1, result.skippedFiles)
        assertEquals(2, medium.bessources.size)
    }

    @Test
    fun `does not rename outside the location`() {
        // a name that escapes the folder must not reach the file system at all
        givenMarkedMedium(primaryName = "../outside.jpg")
        val outside = File(tempDir, "outside.jpg").apply { createNewFile() }

        service.deleteMarkedMedium(10)

        assertTrue(outside.isFile)
    }

    @Test
    fun `does not rename when the storage has no FS location`() {
        val medium = givenMarkedMedium(primaryName = "shot.jpg", withoutFsLocations = true)
        File(mainFs, "shot.jpg").createNewFile()

        val result = service.deleteMarkedMedium(10)

        assertEquals(0, result.renamedFiles)
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