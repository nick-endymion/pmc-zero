package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [BessourceFiles], against real files in a temp folder and a mocked repository.
 *
 * What decides where a file is and whether it may be touched is one place for a reason, so these are
 * about the two decisions and about the third one that a name arriving from an url makes necessary:
 * a name that is not (yet) a bessource's.
 */
class BessourceFilesTests {

    @TempDir
    lateinit var tempDir: File

    private val storageRepository: StorageRepository = mock()

    private lateinit var files: BessourceFiles

    /** the MAIN_FS folder, which is where a primary file lives */
    private val mainFs: File by lazy { File(tempDir, "main").apply { mkdirs() } }

    /** the TN_FS folder, which is where a thumbnail lives */
    private val tnFs: File by lazy { File(tempDir, "tn").apply { mkdirs() } }

    @BeforeEach
    fun setUp() {
        files = BessourceFiles(StorageService(storageRepository))
        givenStorage(mainFs, tnFs)
    }

    // -------------------------------------------------------------------------------------
    // A bessource and the file behind it
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the file of a primary bessource`() {
        givenFile("bilder/a.jpg", "inhalt")

        assertEquals("inhalt", files.fileOf(bessource("bilder/a.jpg", RessType.PRIMARY))?.readText())
    }

    /** A thumbnail is not looked for in the MAIN_FS folder but in the TN_FS one. */
    @Test
    fun `finds the file of a thumbnail in the thumbnail folder`() {
        File(tnFs, "bilder").mkdirs()
        File(tnFs, "bilder/a.jpg").writeText("thumb")

        assertEquals("thumb", files.fileOf(bessource("bilder/a.jpg", RessType.TN))?.readText())
    }

    @Test
    fun `finds nothing for a bessource whose file is not there`() {
        assertNull(files.fileOf(bessource("bilder/weg.jpg", RessType.PRIMARY)))
    }

    @Test
    fun `finds nothing for a bessource without a name`() {
        assertNull(files.fileOf(bessource(null, RessType.PRIMARY)))
    }

    @Test
    fun `finds nothing for a bessource of a ressource type that has no folder`() {
        givenFile("bilder/a.jpg", "x")

        assertNull(files.fileOf(bessource("bilder/a.jpg", RessType.URL)))
    }

    // -------------------------------------------------------------------------------------
    // A name and a storage, i.e. what an url is
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the file a name stands for in the main folder of a storage`() {
        givenFile("bilder/a.jpg", "inhalt")

        assertEquals("inhalt", files.mainFileOf(1, "bilder/a.jpg")?.readText())
    }

    /**
     * The reason the endpoint takes the rest of the path: a bessource name is a path below the
     * location and not a file name alone.
     */
    @Test
    fun `finds a file whose name names directories of its own`() {
        givenFile("imagegap4/abc/984580928.jpg", "bild")

        assertEquals("bild", files.mainFileOf(1, "imagegap4/abc/984580928.jpg")?.readText())
    }

    @Test
    fun `finds a file directly below the location`() {
        givenFile("a.jpg", "bild")

        assertEquals("bild", files.mainFileOf(1, "a.jpg")?.readText())
    }

    /** A storage may keep its files in several places, and the one in use is the one served. */
    @Test
    fun `finds the file in the location that is in use`() {
        givenStorage(mainFs, tnFs)
        File(mainFs, "bilder").mkdirs()
        File(mainFs, "bilder/a.jpg").writeText("der richtige")

        assertEquals("der richtige", files.mainFileOf(1, "bilder/a.jpg")?.readText())
    }

    /** A second MAIN_FS location that is not in use is a place nothing of this call reads. */
    @Test
    fun `finds nothing when the main location is not the one in use`() {
        val notInUse = File(tempDir, "anders").apply { mkdirs() }
        File(notInUse, "bilder").mkdirs()
        File(notInUse, "bilder/a.jpg").writeText("x")
        givenStorageWithAnUnusedMainLocation(notInUse)

        assertNull(files.mainFileOf(1, "bilder/a.jpg"))
    }

    @Test
    fun `finds nothing when a storage has no main location at all`() {
        givenStorageWithoutAMainFolder()

        assertNull(files.mainFileOf(1, "bilder/a.jpg"))
    }

    /** The thumbnails of a storage are not served from the main call, whatever they are named. */
    @Test
    fun `does not serve a file that is only in the thumbnail folder`() {
        File(tnFs, "bilder").mkdirs()
        File(tnFs, "bilder/a.jpg").writeText("thumb")

        assertNull(files.mainFileOf(1, "bilder/a.jpg"))
    }

    @Test
    fun `finds nothing for an unknown storage`() {
        assertThrows<NotFoundException> {
            files.mainFileOf(99, "bilder/a.jpg")
        }
    }

    // -------------------------------------------------------------------------------------
    // What a name may not reach
    // -------------------------------------------------------------------------------------

    /** A name is free text from a url, so `..` in it must not climb out of the location. */
    @Test
    fun `finds nothing for a name that climbs out of the location`() {
        File(tempDir, "ausserhalb.jpg").writeText("geheim")
        givenFile("bilder/a.jpg", "x")

        assertNull(files.mainFileOf(1, "../ausserhalb.jpg"))
    }

    @Test
    fun `finds nothing for a name that climbs out of the location further down`() {
        File(tempDir, "ausserhalb.jpg").writeText("geheim")

        assertNull(files.mainFileOf(1, "bilder/../../ausserhalb.jpg"))
    }

    /** An absolute name is a name as well, and is resolved inside the location like any other. */
    @Test
    fun `finds nothing for an absolute name pointing elsewhere`() {
        val elsewhere = File(tempDir, "anders").apply { mkdirs() }
        File(elsewhere, "a.jpg").writeText("geheim")

        assertNull(files.mainFileOf(1, File(elsewhere, "a.jpg").absolutePath))
    }

    /**
     * A folder is not a file, so a name that lands on one answers nothing rather than the content of
     * a directory.
     */
    @Test
    fun `finds nothing for a name that is a folder`() {
        File(mainFs, "bilder").mkdirs()

        assertNull(files.mainFileOf(1, "bilder"))
    }

    @Test
    fun `finds nothing for a name ending in a slash`() {
        givenFile("bilder/a.jpg", "x")

        assertNull(files.mainFileOf(1, "bilder/"))
    }

    @Test
    fun `finds nothing for an empty name`() {
        givenFile("bilder/a.jpg", "x")

        assertNull(files.mainFileOf(1, ""))
    }

    @Test
    fun `finds nothing for a name that is missing`() {
        assertNull(files.mainFileOf(1, null))
    }

    /** The dots of a relative name resolve inside the location, so such a name is served like any other. */
    @Test
    fun `finds a file whose name has dots that stay inside the location`() {
        givenFile("bilder/a.jpg", "x")

        assertEquals("x", files.mainFileOf(1, "./bilder/./a.jpg")?.readText())
    }

    // -------------------------------------------------------------------------------------
    // A second file beside the first
    // -------------------------------------------------------------------------------------

    /**
     * The destination of a move is a path and not a file, so it is answered even when nothing is on it
     * yet, while [BessourceFiles.fileOf] is not.
     */
    @Test
    fun `answers a destination that is not there yet`() {
        assertEquals("DELETED/bilder/a.jpg", files.destinationOf(bessource("bilder/a.jpg", RessType.PRIMARY), "DELETED/bilder/a.jpg")
            ?.toRelativeString(mainFs.canonicalFile)?.replace(File.separatorChar, '/'))
    }

    @Test
    fun `answers no destination outside the location`() {
        assertNull(files.destinationOf(bessource("bilder/a.jpg", RessType.PRIMARY), "../ausserhalb.jpg"))
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a file below the MAIN_FS folder, with the directories on the way */
    private fun givenFile(name: String, content: String) {
        File(mainFs, name).apply { parentFile?.mkdirs() }.writeText(content)
    }

    /**
     * A bessource on the stored storage, naming [name] of the ressource type [ressType].
     *
     * Only its id is asked of the storage, since the locations are read through [StorageService]
     * rather than off the row.
     */
    private fun bessource(name: String?, ressType: RessType) = Bessource().also {
        it.id = 7
        it.name = name
        it.ressType = ressType.i
        it.encrypted = false
        it.storage = storage()
    }

    private fun storage() = Storage().also {
        it.id = 1
        it.name = "regal"
    }

    private fun location(folder: File, locationType: Int, id: Int) = Location().also {
        it.id = id
        it.name = folder.name
        it.uri = folder.absolutePath
        it.locationType = locationType
        it.inuse = 1
    }

    /** a storage whose MAIN_FS and TN_FS locations are the folders given, both in use */
    private fun givenStorage(main: File, tn: File?) {
        whenever(storageRepository.findByIdOrNullWithLocations(1)).thenReturn(
            Storage().also {
                it.id = 1
                it.name = "regal"
                it.locations = listOfNotNull(
                    location(main, LocationType.MAIN_FS.i, 1),
                    tn?.let { location(it, LocationType.TN_FS.i, 2) }
                )
            }
        )
    }

    /** a storage whose MAIN_FS location is there but not in use, i.e. there is no folder to read */
    private fun givenStorageWithAnUnusedMainLocation(unused: File) {
        whenever(storageRepository.findByIdOrNullWithLocations(1)).thenReturn(
            Storage().also {
                it.id = 1
                it.name = "regal"
                it.locations = listOf(location(unused, LocationType.MAIN_FS.i, 1).also { l -> l.inuse = 0 })
            }
        )
    }

    /** a storage with only a thumbnail folder, i.e. one that has never held a primary file */
    private fun givenStorageWithoutAMainFolder() {
        whenever(storageRepository.findByIdOrNullWithLocations(1)).thenReturn(
            Storage().also {
                it.id = 1
                it.name = "regal"
                it.locations = listOf(location(tnFs, LocationType.TN_FS.i, 2))
            }
        )
    }
}