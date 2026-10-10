package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.FileInfo
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.FileInfoRepository
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [FileInfoService], against real files in a temp folder and a mocked repository.
 *
 * The files are real rather than described, since every property this records is a fact about a file on
 * disk: a size of a mocked one would be a number the service invented, and a hash of it would be a hash
 * of nothing.
 */
class FileInfoServiceTests {

    @TempDir
    lateinit var tempDir: File

    private val fileInfoRepository: FileInfoRepository = mock()
    private val bessourceRepository: BessourceRepository = mock()
    private val storageRepository: StorageRepository = mock()

    private lateinit var service: FileInfoService

    /** the MAIN_FS folder, which is where a primary bessource lives */
    private val mainFs: File by lazy { File(tempDir, "main").apply { mkdirs() } }

    @BeforeEach
    fun setUp() {
        service = FileInfoService(
            fileInfoRepository,
            bessourceRepository,
            BessourceFiles(StorageService(storageRepository))
        )

        givenStorageWithAFolder(mainFs)

        // a save hands the row back the way a repository would
        whenever(fileInfoRepository.save(any<FileInfo>())).thenAnswer { it.getArgument(0) }
    }

    // -------------------------------------------------------------------------------------
    // What it records
    // -------------------------------------------------------------------------------------

    @Test
    fun `records the size of the file`() {
        givenBessource(3, "bilder/a.jpg", content = "0123456789")

        val info = service.recordFor(3)

        assertEquals(10, info.size)
    }

    @Test
    fun `records a hash of the content of the file`() {
        givenBessource(3, "bilder/a.jpg", content = "0123456789")

        val info = service.recordFor(3)

        assertEquals(sha256Of("0123456789"), info.hash)
    }

    /** SHA-256 rather than a hash of a name, so two files of the same content agree. */
    @Test
    fun `records the same hash for two files of the same content`() {
        givenBessource(3, "bilder/a.jpg", content = "gleich")
        givenBessource(4, "bilder/b.jpg", content = "gleich")

        assertEquals(service.recordFor(3).hash, service.recordFor(4).hash)
    }

    /** And a different one for different content, since a hash that says nothing about the content is no use. */
    @Test
    fun `records a different hash for different content`() {
        givenBessource(3, "bilder/a.jpg", content = "eins")
        givenBessource(4, "bilder/b.jpg", content = "zwei")

        assertTrue(service.recordFor(3).hash != service.recordFor(4).hash)
    }

    /**
     * The timestamps are the file's own, read from the file system, not the time of this call.
     *
     * Compared against what the file system itself reports, within a second: a timestamp is written with
     * whatever precision the file system has, and asking for exact equality would be asking about that
     * rather than about this.
     */
    @Test
    fun `records the changed time of the file`() {
        val file = givenBessource(3, "bilder/a.jpg", content = "x")

        val info = service.recordFor(3)

        val expected = java.nio.file.Files.getLastModifiedTime(file.toPath())
            .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        val recorded = assertNotNull(info.fileChangedAt)
        assertTrue(
            recorded.isAfter(expected.minusSeconds(2)) && recorded.isBefore(expected.plusSeconds(2)),
            "expected about $expected but was $recorded"
        )
    }

    @Test
    fun `records the creation time of the file when the file system keeps one`() {
        givenBessource(3, "bilder/a.jpg", content = "x")

        val info = service.recordFor(3)

        // windows and ext4 keep it, and this suite runs on both; a file system that does not is
        // answered with null rather than with something made up
        if (java.nio.file.Files.getFileAttributeView(
                File(tempDir, "main/bilder/a.jpg").toPath(),
                java.nio.file.attribute.FileAttributeView::class.java
            ) != null
        ) {
            assertNotNull(info.fileCreatedAt, "a creation time is available here")
        }
    }

    @Test
    fun `records the bessource the file belongs to`() {
        givenBessource(3, "bilder/a.jpg", content = "x")

        assertEquals(3, service.recordFor(3).bessourceId)
    }

    // -------------------------------------------------------------------------------------
    // Recording again
    // -------------------------------------------------------------------------------------

    /**
     * A row is a fact about the file now, so a bessource that has one is updated rather than joined by
     * a second. Two rows for one bessource would also not be allowed by the column, so this is the
     * behaviour that keeps the table one to one.
     */
    @Test
    fun `updates the row of a bessource that has one`() {
        givenBessource(3, "bilder/a.jpg", content = "x")
        val existing = FileInfo().also {
            it.id = 55
            it.bessourceId = 3
            it.size = 1
            it.hash = "der alte"
        }
        whenever(fileInfoRepository.findByBessourceId(3)).thenReturn(existing)

        val info = service.recordFor(3)

        assertEquals(55, info.id, "the row that was there is the one that is written to")
        assertEquals("der alte" != info.hash, true, "the hash was read again")
    }

    @Test
    fun `adds a row for a bessource that has none`() {
        givenBessource(3, "bilder/a.jpg", content = "x")
        whenever(fileInfoRepository.findByBessourceId(3)).thenReturn(null)

        val info = service.recordFor(3)

        assertNull(info.id, "the database decides the id of a new row")
        assertEquals(3, info.bessourceId)
    }

    /** A file that changed is recognised as changed, which is what the second call is for. */
    @Test
    fun `records a different hash after the file changed`() {
        givenBessource(3, "bilder/a.jpg", content = "vorher")
        val first = service.recordFor(3)

        File(tempDir, "main/bilder/a.jpg").writeText("nachher")

        assertTrue(first.hash != service.recordFor(3).hash, "the hash of the new content")
    }

    @Test
    fun `records nothing when the bessource is unknown`() {
        whenever(bessourceRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.recordFor(99)
        }

        verify(fileInfoRepository, never()).save(any<FileInfo>())
    }

    // -------------------------------------------------------------------------------------
    // What it refuses
    // -------------------------------------------------------------------------------------

    /** A bessource whose file is gone has no size, no timestamps and no hash, and says so. */
    @Test
    fun `refuses a bessource whose file is not there`() {
        givenBessource(3, name = "bilder/weg.jpg", write = false)

        val e = assertThrows<NotAccessibleException> {
            service.recordFor(3)
        }

        assertTrue(e.message!!.contains("no file on disk"))
    }

    @Test
    fun `refuses a bessource without a name`() {
        givenBessource(3, name = null)

        assertThrows<NotAccessibleException> {
            service.recordFor(3)
        }
    }

    /** A name is free text, so one that climbs out of the location has no file to read. */
    @Test
    fun `refuses a bessource whose name leaves the location`() {
        givenBessource(3, name = "../../../ausserhalb.jpg")

        assertThrows<NotAccessibleException> {
            service.recordFor(3)
        }
    }

    /** A thumbnail lives in the TN_FS folder, not the MAIN_FS one, and is found there. */
    @Test
    fun `records the file of a thumbnail bessource`() {
        val tnFs = File(tempDir, "tn").apply { mkdirs() }
        File(tnFs, "bilder").mkdirs()
        File(tnFs, "bilder/a.jpg").writeText("thumbnail")
        givenStorageWithAFolder(mainFs, tnFs)
        givenBessource(3, "bilder/a.jpg", content = null, ressType = RessType.TN, write = false)

        val info = service.recordFor(3)

        assertEquals("thumbnail".length.toLong(), info.size)
    }

    @Test
    fun `refuses a bessource of a ressource type that has no folder`() {
        givenBessource(3, "bilder/a.jpg", content = "x", ressType = RessType.URL)

        assertThrows<NotAccessibleException> {
            service.recordFor(3)
        }
    }

    // -------------------------------------------------------------------------------------
    // Reading a row back
    // -------------------------------------------------------------------------------------

    /** Null rather than a row of zeroes, so "not looked at" and "looked at" are told apart. */
    @Test
    fun `answers no row for a bessource nobody has looked at`() {
        whenever(fileInfoRepository.findByBessourceId(3)).thenReturn(null)

        assertNull(service.findByBessourceId(3))
    }

    @Test
    fun `answers the row of a bessource that has one`() {
        val info = FileInfo().also {
            it.id = 55
            it.bessourceId = 3
            it.size = 7
        }
        whenever(fileInfoRepository.findByBessourceId(3)).thenReturn(info)

        assertEquals(7, service.findByBessourceId(3)!!.size)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a bessource with [id] naming [name], its file written unless [write] is false */
    private fun givenBessource(
        id: Int,
        name: String?,
        content: String? = null,
        ressType: RessType = RessType.PRIMARY,
        write: Boolean = true
    ): File {
        // on findById and not on findByIdOrNull: the latter is an extension whose body calls the
        // former, so a stub on it is registered on the findById inside it and then answers a bessource
        // where that method is declared to return an Optional
        whenever(bessourceRepository.findById(id)).thenReturn(Optional.of(bessourceOf(id, name, ressType)))

        if (name != null && write) {
            File(mainFs, name).apply { parentFile?.mkdirs() }.writeText(content ?: "x")
        }

        return File(mainFs, name ?: "")
    }

    private fun bessourceOf(id: Int, name: String?, ressType: RessType) = Bessource().also {
        it.id = id
        it.name = name
        it.ressType = ressType.i
        it.encrypted = false
        it.storage = storageOf(mainFs)
    }

    /** a storage whose MAIN_FS and TN_FS locations are the folders given */
    private fun givenStorageWithAFolder(main: File, tn: File? = null) {
        whenever(storageRepository.findByIdOrNullWithLocations(1)).thenReturn(
            Storage().also {
                it.id = 1
                it.name = "regal"
                it.locations = listOfNotNull(
                    locationOf(main, LocationType.MAIN_FS.i, 1),
                    tn?.let { tnFolder -> locationOf(tnFolder, LocationType.TN_FS.i, 2) }
                )
            }
        )
    }

    /** the storage the bessources sit on, built on its own since the fixture above is the stored one */
    private fun storageOf(folder: File) = Storage().also {
        it.id = 1
        it.name = "regal"
        it.locations = listOfNotNull(locationOf(folder, LocationType.MAIN_FS.i, 1))
    }

    private fun locationOf(folder: File, locationType: Int, id: Int) = Location().also {
        it.id = id
        it.name = folder.name
        it.uri = folder.absolutePath
        it.locationType = locationType
        it.inuse = 1
    }

    /** the hash the same content has when computed here, as a check that the service hashes it the same way */
    private fun sha256Of(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
