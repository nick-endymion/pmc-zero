package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Optional
import javax.imageio.ImageIO

/**
 * Unit tests for [ThumbnailService.createThumbnail].
 *
 * Real image files are written into a temp directory, because the behaviour under test is a file
 * being produced: the pixels, the format and the path all matter and a mocked file system could
 * only assert that the right calls were made, not that any of it works.
 *
 * A real [LocationService] and [StorageService] over mocked repositories are used, for the same
 * reason as in [MediaServiceTest]: the point is that the thumbnail path and url go through the same
 * location resolution as every other url.
 */
class ThumbnailServiceTest {

    private val mediaRepository: MediaRepository = mock()
    private val bessourceRepository: BessourceRepository = mock()
    private val storageRepository: StorageRepository = mock()
    private val msetRepository: MsetRepository = mock()

    private lateinit var service: ThumbnailService

    /** Real, over a mocked repository: the batch call goes through findById(id, withMedia = true). */
    private lateinit var msetService: MsetService

    @TempDir
    lateinit var tempDir: File

    private var nextStorageId = 1

    /** Distinct per fixture, so a test can register two media without the second shadowing the first. */
    private var nextMediumId = 1

    @BeforeEach
    fun setUp() {
        val locationService = LocationService(mock(), StorageService(storageRepository), bessourceRepository)
        val mediaService = MediaService(mediaRepository, bessourceRepository, locationService)
        msetService = MsetService(mediaRepository, msetRepository, mediaService)
        service = ThumbnailService(
            mediaRepository,
            bessourceRepository,
            StorageService(storageRepository),
            locationService,
            msetService
        )
        // save() is what the service calls to persist a bessource; answering with its argument keeps
        // the in-memory list and the assertions in step without a database
        whenever(bessourceRepository.save(any<Bessource>())).thenAnswer { it.getArgument(0) }
    }

    // -------------------------------------------------------------------------------------
    // The file that comes out
    // -------------------------------------------------------------------------------------

    @Test
    fun `writes the thumbnail into the TN_FS location`() {
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        assertTrue(File(fixture.tnFs.uri, "shot.jpg").isFile)
    }

    @Test
    fun `writes a file that is actually a readable image`() {
        val fixture = givenMedium("shot.jpg", image = image(1200, 900, Color.RED))

        service.createThumbnail(fixture.mediumId)

        val written = ImageIO.read(File(fixture.tnFs.uri, "shot.jpg"))
        assertEquals(512, written.width)
        assertEquals(384, written.height)
    }

    @Test
    fun `answers the size of the thumbnail it wrote`() {
        val fixture = givenMedium("shot.jpg", image = image(1200, 900))

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals(512, result.width)
        assertEquals(384, result.height)
    }

    @Test
    fun `keeps the aspect ratio of a portrait image`() {
        val fixture = givenMedium("shot.jpg", image = image(900, 1200))

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals(384, result.width)
        assertEquals(512, result.height)
    }

    @Test
    fun `bounds the longest edge, so a thumbnail is never larger than 512`() {
        val fixture = givenMedium("shot.jpg", image = image(4000, 3000))

        service.createThumbnail(fixture.mediumId)

        val written = ImageIO.read(File(fixture.tnFs.uri, "shot.jpg"))
        assertTrue(maxOf(written.width, written.height) <= 512)
    }

    @Test
    fun `does not enlarge an image that already fits`() {
        val fixture = givenMedium("small.jpg", image = image(100, 80))

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals(100, result.width)
        assertEquals(80, result.height)
    }

    @Test
    fun `scales an image that fits on one edge only`() {
        val fixture = givenMedium("wide.jpg", image = image(600, 100))

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals(512, result.width)
        assertEquals(85, result.height)
    }

    @Test
    fun `gives a transparent source a white background rather than a black one`() {
        // JPEG cannot store alpha, so an image with one has to be painted onto an opaque canvas
        val transparent = BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB)
        val fixture = givenMedium("logo.png", image = transparent, imageFormat = "png")

        service.createThumbnail(fixture.mediumId)

        val written = ImageIO.read(File(fixture.tnFs.uri, "logo.jpg"))
        // the top left pixel was fully transparent, so it can only come out white or black here
        assertEquals(255, Color(written.getRGB(0, 0)).red)
        assertEquals(255, Color(written.getRGB(0, 0)).blue)
    }

    // -------------------------------------------------------------------------------------
    // The directory it mirrors
    // -------------------------------------------------------------------------------------

    @Test
    fun `mirrors the subdirectory of the primary file`() {
        val fixture = givenMedium("2020/jan/shot.jpg")

        service.createThumbnail(fixture.mediumId)

        assertTrue(File(fixture.tnFs.uri, "2020/jan/shot.jpg").isFile)
    }

    @Test
    fun `creates the missing directories on the way`() {
        val fixture = givenMedium("a/b/c/shot.jpg")
        // the TN location itself exists but nothing below it does
        assertFalse(File(fixture.tnFs.uri, "a").exists())

        service.createThumbnail(fixture.mediumId)

        assertTrue(File(fixture.tnFs.uri, "a/b/c").isDirectory)
    }

    @Test
    fun `creates the TN location folder itself when it does not exist yet`() {
        val fixture = givenMedium("shot.jpg", tnFsExists = false)

        service.createThumbnail(fixture.mediumId)

        assertTrue(File(fixture.tnFs.uri, "shot.jpg").isFile)
    }

    @Test
    fun `keeps two same named files of different subdirectories apart`() {
        val first = givenMedium("a/shot.jpg")
        val second = givenMedium("b/shot.jpg")

        service.createThumbnail(first.mediumId)
        service.createThumbnail(second.mediumId)

        // both exist, each below its own subdirectory, so neither has overwritten the other
        assertTrue(File(first.tnFs.uri, "a/shot.jpg").isFile)
        assertTrue(File(second.tnFs.uri, "b/shot.jpg").isFile)
    }

    @Test
    fun `names the thumbnail of a png primary file as a jpg`() {
        val fixture = givenMedium("shot.png")

        val result = service.createThumbnail(fixture.mediumId)

        // the bytes are a JPEG, so the stored name has to say so
        assertEquals("shot.jpg", result.name)
        assertTrue(File(fixture.tnFs.uri, "shot.jpg").isFile)
        assertFalse(File(fixture.tnFs.uri, "shot.png").exists())
    }

    @Test
    fun `names the thumbnail of a name without extension as a jpg`() {
        val fixture = givenMedium("shot")

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals("shot.jpg", result.name)
    }

    @Test
    fun `leaves a name without extension in a subdirectory alone`() {
        val fixture = givenMedium("2020/shot")

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals("2020/shot.jpg", result.name)
    }

    @Test
    fun `keeps a dot in a directory name out of the file name`() {
        val fixture = givenMedium("scan.v2/shot.png")

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals("scan.v2/shot.jpg", result.name)
    }

    @Test
    fun `normalizes windows separators into a path below the location`() {
        val fixture = givenMedium("2020\\jan\\shot.jpg")

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals("2020/jan/shot.jpg", result.name)
        assertTrue(File(fixture.tnFs.uri, "2020").isDirectory)
    }

    // -------------------------------------------------------------------------------------
    // The bessource it leaves behind
    // -------------------------------------------------------------------------------------

    @Test
    fun `points a new TN bessource at the thumbnail`() {
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        val saved = argumentCaptor<Bessource>()
        verify(bessourceRepository).save(saved.capture())
        assertEquals(RessType.TN.i, saved.firstValue.ressType)
        assertEquals("shot.jpg", saved.firstValue.name)
        assertEquals(fixture.mediumId, saved.firstValue.medium!!.id)
    }

    @Test
    fun `puts the new TN bessource on the storage of the primary`() {
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        val saved = argumentCaptor<Bessource>()
        verify(bessourceRepository).save(saved.capture())
        assertEquals(fixture.storage.id, saved.firstValue.storage.id)
    }

    @Test
    fun `adds the new bessource to the medium it was given`() {
        // so a caller that keeps using the medium it passed in sees the thumbnail
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        assertTrue(fixture.medium.bessources.any { it.ressType == RessType.TN.i })
    }

    @Test
    fun `updates the existing TN bessource instead of adding a second one`() {
        val fixture = givenMedium("shot.jpg", withStaleTn = true)

        service.createThumbnail(fixture.mediumId)

        val saved = argumentCaptor<Bessource>()
        verify(bessourceRepository, times(1)).save(saved.capture())
        assertEquals(99, saved.firstValue.id)
        assertEquals("shot.jpg", saved.firstValue.name)
        // a medium can only have one TN bessource, providePhysicalRessources derives the thumbnail
        // from the primary only when there is none, so a second row would be unreachable
        assertEquals(2, fixture.medium.bessources.size)
    }

    @Test
    fun `repoints an existing TN bessource that named a different file`() {
        val fixture = givenMedium("shot.jpg", withStaleTn = true)

        service.createThumbnail(fixture.mediumId)

        assertTrue(File(fixture.tnFs.uri, "old.jpg").isFile.not())
        assertEquals("shot.jpg", fixture.staleTn!!.name)
    }

    @Test
    fun `does not touch the primary bessource`() {
        // the thumbnail is a second bessource, the file it was made from has to stay as it was
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        val saved = argumentCaptor<Bessource>()
        verify(bessourceRepository).save(saved.capture())
        assertEquals(RessType.PRIMARY.i, fixture.primary.ressType)
        assertEquals("shot.jpg", fixture.primary.name)
        assertEquals(1, saved.firstValue.medium!!.bessources.first { it.ressType == RessType.PRIMARY.i }.id)
    }

    // -------------------------------------------------------------------------------------
    // The url it answers
    // -------------------------------------------------------------------------------------

    @Test
    fun `answers the thumbnail url below the TN_FS location`() {
        val fixture = givenMedium("2020/shot.jpg")

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals("2020/shot.jpg", result.name)
        // LocationService.url joins with a '/', the same separator a bessource name holds
        assertEquals("${fixture.tnFs.uri}/2020/shot.jpg", result.url)
    }

    @Test
    fun `answers a url the media detail page can serve straight away`() {
        // what the thumbnail url resolves to has to be the file that was just written
        val fixture = givenMedium("shot.jpg")

        val result = service.createThumbnail(fixture.mediumId)

        val file = File(result.url)
        assertTrue(file.isFile)
        assertTrue(ImageIO.read(file) != null)
    }

    // -------------------------------------------------------------------------------------
    // An existing thumbnail
    // -------------------------------------------------------------------------------------

    @Test
    fun `leaves an existing thumbnail alone`() {
        val fixture = givenMedium("shot.jpg", image = image(600, 600))
        val existing = File(fixture.tnFs.uri, "shot.jpg").apply { parentFile.mkdirs() }
        ImageIO.write(image(10, 10, Color.GREEN), "jpg", existing)
        val writtenAt = existing.lastModified()

        service.createThumbnail(fixture.mediumId)

        // untouched, so the 10x10 file is still a 10x10 file
        assertEquals(10, ImageIO.read(existing).width)
        assertEquals(writtenAt, existing.lastModified())
    }

    @Test
    fun `reports that nothing was created when the thumbnail was already there`() {
        val fixture = givenMedium("shot.jpg", image = image(600, 600))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        val result = service.createThumbnail(fixture.mediumId)

        assertFalse(result.created)
    }

    @Test
    fun `reports the size of an existing thumbnail`() {
        val fixture = givenMedium("shot.jpg", image = image(600, 600))
        ImageIO.write(image(120, 90), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        val result = service.createThumbnail(fixture.mediumId)

        assertEquals(120, result.width)
        assertEquals(90, result.height)
    }

    @Test
    fun `reports that it created a thumbnail when there was none`() {
        val fixture = givenMedium("shot.jpg")

        val result = service.createThumbnail(fixture.mediumId)

        assertTrue(result.created)
    }

    @Test
    fun `still records the bessource when the file was already there`() {
        // a file with no bessource is not reachable through any url, so the row is what makes it usable
        val fixture = givenMedium("shot.jpg", image = image(600, 600))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        service.createThumbnail(fixture.mediumId)

        verify(bessourceRepository, times(1)).save(any())
    }

    @Test
    fun `regenerates an existing thumbnail when forced`() {
        val fixture = givenMedium("shot.jpg", image = image(1200, 900))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        val result = service.createThumbnail(fixture.mediumId, force = true)

        assertTrue(result.created)
        assertEquals(512, ImageIO.read(File(fixture.tnFs.uri, "shot.jpg")).width)
    }

    @Test
    fun `overwrites an existing thumbnail when forced`() {
        // renameTo refuses to overwrite, so the force path has to cope with it explicitly
        val fixture = givenMedium("shot.jpg", image = image(1200, 900))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        service.createThumbnail(fixture.mediumId, force = true)

        assertEquals(384, ImageIO.read(File(fixture.tnFs.uri, "shot.jpg")).height)
    }

    @Test
    fun `leaves no temporary file behind`() {
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        assertEquals(listOf("shot.jpg"), File(fixture.tnFs.uri).list()!!.toList())
    }

    @Test
    fun `leaves no temporary file behind on a forced regeneration`() {
        // the temp file is created next to the target, so an overwrite has to clean up as well
        val fixture = givenMedium("shot.jpg", image = image(1200, 900))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))

        service.createThumbnail(fixture.mediumId, force = true)

        assertEquals(listOf("shot.jpg"), File(fixture.tnFs.uri).list()!!.toList())
    }

    // -------------------------------------------------------------------------------------
    // What cannot be thumbnailed
    // -------------------------------------------------------------------------------------

    @Test
    fun `throws NotFoundException for an unknown medium`() {
        whenever(mediaRepository.findById(404)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.createThumbnail(404)
        }
    }

    @Test
    fun `throws when the medium has no primary bessource`() {
        val fixture = givenMedium("shot.jpg", createPrimaryFile = true, registerPrimary = false)

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }
    }

    @Test
    fun `throws when the storage has only an HTTP main location`() {
        // the primary lives behind http, so there is no file on disk to make a thumbnail from
        val fixture = givenMedium(tnFsExistsAsLocation = false, httpMainOnly = true)

        val e = assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertTrue(e.message!!.contains("MAIN_FS"), e.message)
    }

    @Test
    fun `throws when the storage has no TN_FS location`() {
        val fixture = givenMedium(tnFsExistsAsLocation = false)

        val e = assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertTrue(e.message!!.contains("TN_FS"), e.message)
    }

    @Test
    fun `throws when the MAIN_FS location is not in use`() {
        val fixture = givenMedium(mainFsInUse = false)

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }
    }

    @Test
    fun `throws when the TN_FS location is not in use`() {
        val fixture = givenMedium(tnFsInUse = false)

        val e = assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertTrue(e.message!!.contains("TN_FS"), e.message)
    }

    @Test
    fun `throws when the primary file is missing`() {
        val fixture = givenMedium("gone.jpg", createPrimaryFile = false)

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }
    }

    @Test
    fun `throws when the primary file is not an image`() {
        val fixture = givenMedium("notes.txt", primaryContent = "not an image")

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }
    }

    @Test
    fun `writes nothing when the primary file is not an image`() {
        val fixture = givenMedium("notes.txt", primaryContent = "not an image")

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertFalse(File(fixture.tnFs.uri, "notes.jpg").exists())
    }

    @Test
    fun `saves no bessource when the thumbnail could not be written`() {
        val fixture = givenMedium("notes.txt", primaryContent = "not an image")

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        verify(bessourceRepository, never()).save(any())
    }

    @Test
    fun `throws when the thumbnail name escapes the TN location`() {
        // a bessource name is free text, and a generated file must never land outside its location
        val fixture = givenMedium("../../escaped.jpg")

        val e = assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertTrue(e.message!!.contains("escapes"), e.message)
    }

    @Test
    fun `writes nothing outside the TN location for an escaping name`() {
        val fixture = givenMedium("../../escaped.jpg")

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        assertFalse(File(tempDir, "escaped.jpg").exists())
    }

    @Test
    fun `saves no bessource when the thumbnail path escapes`() {
        val fixture = givenMedium("../../escaped.jpg")

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }

        verifyNoInteractions(bessourceRepository)
    }

    @Test
    fun `throws when the primary bessource has no storage`() {
        // storage is lateinit, so reading it on a bessource built without one would throw rather
        // than return null, which storageOrNull exists to prevent
        val fixture = givenMedium(primaryWithoutStorage = true)

        assertThrows<NotAccessibleException> {
            service.createThumbnail(fixture.mediumId)
        }
    }

    // -------------------------------------------------------------------------------------
    // Nothing to do with the database
    // -------------------------------------------------------------------------------------

    @Test
    fun `asks for nothing when the thumbnail is already there`() {
        val fixture = givenMedium("shot.jpg", image = image(600, 600))
        ImageIO.write(image(10, 10), "jpg", File(fixture.tnFs.uri, "shot.jpg"))
        verify(bessourceRepository, never()).save(any())

        service.createThumbnail(fixture.mediumId)

        // the medium itself is still read, only no write happens on the skipped path
        verify(bessourceRepository, times(1)).save(any())
    }

    @Test
    fun `does not ask the repository for any bessource of the medium`() {
        // everything needed is on the medium and its storage, so no extra query is worth making
        val fixture = givenMedium("shot.jpg")

        service.createThumbnail(fixture.mediumId)

        verify(bessourceRepository, never()).findNamesOfExistingMedia(any(), any(), any())
    }

    // -------------------------------------------------------------------------------------
    // A whole mset at once
    // -------------------------------------------------------------------------------------

    @Test
    fun `creates a thumbnail for every medium of the mset`() {
        val fixtures = givenMsetOf("a.jpg", "b.jpg", "c.jpg")

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(3, result.thumbnails.size)
        // every medium got a file of its own, named after the primary
        assertTrue(fixtures.all { File(it.tnFs.uri, it.thumbnailFileName).isFile })
    }

    @Test
    fun `answers the id of the mset it was asked about`() {
        givenMsetOf("a.jpg")

        assertEquals(MSET_ID, service.createThumbnailsOfMset(MSET_ID).msetId)
    }

    @Test
    fun `counts every medium of the mset`() {
        givenMsetOf("a.jpg", "b.jpg", "c.jpg")

        assertEquals(3, service.createThumbnailsOfMset(MSET_ID).total)
    }

    @Test
    fun `counts what it created`() {
        givenMsetOf("a.jpg", "b.jpg")

        assertEquals(2, service.createThumbnailsOfMset(MSET_ID).created)
    }

    @Test
    fun `counts what was already there`() {
        val fixtures = givenMsetOf("a.jpg", "b.jpg")
        // one of the two already has its thumbnail on disk
        ImageIO.write(image(10, 10), "jpg", File(fixtures[1].tnFs.uri, "b.jpg"))

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(1, result.created)
        assertEquals(1, result.unchanged)
    }

    @Test
    fun `generates nothing twice over on a second run`() {
        val fixtures = givenMsetOf("a.jpg", "b.jpg")
        service.createThumbnailsOfMset(MSET_ID)
        val writtenAt = fixtures.map { File(it.tnFs.uri, it.thumbnailFileName).lastModified() }

        val second = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(0, second.created)
        assertEquals(2, second.unchanged)
        assertEquals(
            writtenAt,
            fixtures.map { File(it.tnFs.uri, it.thumbnailFileName).lastModified() }
        )
    }

    @Test
    fun `regenerates every thumbnail when forced`() {
        val fixtures = givenMsetOf("a.jpg", "b.jpg")
        service.createThumbnailsOfMset(MSET_ID)
        fixtures.forEach {
            ImageIO.write(image(10, 10), "jpg", File(it.tnFs.uri, it.thumbnailFileName))
        }

        val forced = service.createThumbnailsOfMset(MSET_ID, force = true)

        assertEquals(2, forced.created)
        assertEquals(0, forced.unchanged)
        fixtures.forEach {
            assertEquals(512, ImageIO.read(File(it.tnFs.uri, it.thumbnailFileName)).width)
        }
    }

    @Test
    fun `keeps the subdirectories of the media apart`() {
        val fixtures = givenMsetOf("2020/a.jpg", "2021/b.jpg")

        service.createThumbnailsOfMset(MSET_ID)

        fixtures.forEach { assertTrue(File(it.tnFs.uri, it.thumbnailFileName).isFile) }
    }

    // -- one medium cannot stop the others ------------------------------------------------

    @Test
    fun `does not fail the call for a medium it cannot thumbnail`() {
        // a scanned mset holds media whose files are gone, which must not abort the whole batch
        givenMsetOf("a.jpg", "missing.jpg", "c.jpg", createFilesFor = setOf(0, 2))

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(2, result.thumbnails.size)
        assertEquals(1, result.failed)
        assertEquals(3, result.total)
    }

    @Test
    fun `reports the medium it could not thumbnail`() {
        givenMsetOf("a.jpg", "missing.jpg", createFilesFor = setOf(0))

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(listOf(2), result.failures.map { it.mediumId })
    }

    @Test
    fun `says why a medium could not be thumbnailed`() {
        givenMsetOf("missing.jpg", createFilesFor = emptySet())

        val result = service.createThumbnailsOfMset(MSET_ID)

        // the reason is what tells a caller whether retrying could ever help
        assertTrue(result.failures.single().reason.contains("missing.jpg"), result.failures.single().reason)
    }

    @Test
    fun `names the medium it could not thumbnail`() {
        givenMsetOf("missing.jpg", createFilesFor = emptySet())

        assertEquals("missing.jpg", service.createThumbnailsOfMset(MSET_ID).failures.single().mediumName)
    }

    @Test
    fun `reports a medium with no primary bessource as skipped rather than failed`() {
        // migrated media of type (legacy) folder have no ressources at all, which is a normal state
        givenMsetOf("a.jpg", null, "c.jpg")

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(listOf(2), result.skipped)
        assertEquals(0, result.failed)
        assertEquals(2, result.thumbnails.size)
    }

    @Test
    fun `still creates the other thumbnails when one medium has no primary bessource`() {
        // the skipped one gets no file, the other two do
        val fixtures = givenMsetOf("a.jpg", null, "c.jpg")

        service.createThumbnailsOfMset(MSET_ID)

        assertTrue(File(fixtures[0].tnFs.uri, fixtures[0].thumbnailFileName).isFile)
        assertTrue(File(fixtures[2].tnFs.uri, fixtures[2].thumbnailFileName).isFile)
    }

    @Test
    fun `saves no bessource for a medium it skipped`() {
        givenMsetOf("a.jpg", null)

        service.createThumbnailsOfMset(MSET_ID)

        // one bessource for the one medium that was processed, none for the skipped one
        verify(bessourceRepository, times(1)).save(any())
    }

    @Test
    fun `handles an mset with no media at all`() {
        givenMsetOf()

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(0, result.total)
        assertEquals(0, result.failed)
        assertTrue(result.thumbnails.isEmpty())
    }

    @Test
    fun `does nothing for an mset that does not exist`() {
        whenever(msetRepository.findByIdOrNullWithMedia(404)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.createThumbnailsOfMset(404)
        }
    }

    @Test
    fun `still creates the other thumbnails when a medium has no storage`() {
        givenMsetOf("a.jpg", "b.jpg", primaryWithoutStorageFor = setOf(1))

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(1, result.thumbnails.size)
        assertEquals(1, result.failed)
    }

    @Test
    fun `still creates the other thumbnails when a storage has no TN_FS location`() {
        givenMsetOf("a.jpg", "b.jpg", tnFsMissingFor = setOf(1))

        val result = service.createThumbnailsOfMset(MSET_ID)

        assertEquals(1, result.thumbnails.size)
        assertEquals(1, result.failed)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private class Fixture(
        val mediumId: Int,
        val storage: Storage,
        val mainFs: Location,
        val tnFs: Location,
        val primary: Bessource,
        val medium: Medium,
        /** the pre-existing TN bessource, when [withStaleTn] asked for one */
        val staleTn: Bessource? = null
    ) {
        /** where the thumbnail of this medium's primary belongs, relative to its TN location */
        val thumbnailFileName: String
            get() = primary.name!!.substringBeforeLast('.', primary.name!!) + ".jpg"
    }

    /** The id every mset fixture is registered under, so the batch call has a known key. */
    private val MSET_ID = 500

    /**
     * A medium whose primary bessource points at a real image below a real MAIN_FS location, on a
     * storage that also has a TN_FS location.
     *
     * Every knob here switches off one precondition of the happy path, so a test can isolate the one
     * thing it is about. The files on disk are only written when [createPrimaryFile] is left on,
     * which is what the tests about a missing or unreadable source need to be able to skip.
     *
     * @param image the primary file's content, 800x600 blue when left out
     * @param primaryContent writes these bytes instead of an image, for a source ImageIO cannot read
     * @param extraBessources added to the medium alongside the primary
     * @param primaryWithoutStorage a primary whose `storage` was never set, which is what a
     * bessource built in memory looks like before it is saved
     * @param mainFsInUse false registers the MAIN_FS location but not as in use, so the "in use"
     * filter is what rules it out rather than its absence
     */
    private fun givenMedium(
        primaryName: String = "shot.jpg",
        image: BufferedImage? = null,
        createPrimaryFile: Boolean = true,
        primaryContent: String? = null,
        /** how the primary file is encoded, "jpg" unless a test needs a format that keeps alpha */
        imageFormat: String = "jpg",
        registerPrimary: Boolean = true,
        tnFsExists: Boolean = true,
        mainFsInUse: Boolean = true,
        tnFsInUse: Boolean = true,
        tnFsExistsAsLocation: Boolean = true,
        httpMainOnly: Boolean = false,
        extraBessources: List<Bessource> = emptyList(),
        primaryWithoutStorage: Boolean = false,
        withStaleTn: Boolean = false
    ): Fixture {
        val storageId = nextStorageId++
        val storage = Storage().apply {
            id = storageId
            name = "storage-$storageId"
        }
        // both repository methods are stubbed: findByIdWithLocations is what loads the locations the
        // service resolves against, findById the fallback
        whenever(storageRepository.findByIdOrNullWithLocations(storageId)).thenReturn(storage)
        whenever(storageRepository.findById(storageId)).thenReturn(Optional.of(storage))

        val mainDir = File(tempDir, "main-$storageId").apply { mkdir() }
        val tnDir = File(tempDir, "tn-$storageId").apply { if (tnFsExists) mkdir() }
        val mainFs = location(mainDir.absolutePath, LocationType.MAIN_FS, storage, inuse = if (mainFsInUse) 1 else 0)
        val tnFs = location(tnDir.absolutePath, LocationType.TN_FS, storage, inuse = if (tnFsInUse) 1 else 0)

        // httpMainOnly swaps the MAIN_FS location for an HTTP one: the same shape of storage, but no
        // file system path to read a thumbnail source from
        val mainLocation = if (httpMainOnly)
            location("http://example.org/main", LocationType.MAIN_HTTP, storage)
        else
            mainFs
        // a storage without a TN_FS location at all, which is what rules it out rather than its
        // folder being unusable
        storage.locations = if (tnFsExistsAsLocation) listOf(mainLocation, tnFs) else listOf(mainLocation)

        // built without touching the lateinit storage, which is what a bessource created in memory and
        // not yet saved looks like
        val primary = if (primaryWithoutStorage) Bessource().apply {
            id = 1
            name = primaryName
            ressType = RessType.PRIMARY.i
        } else primaryBessource(primaryName, storage)

        // a TN bessource pointing somewhere else, i.e. the medium has a thumbnail already and it is
        // not where this call would put the new one
        val staleTn = if (withStaleTn) Bessource().apply {
            id = 99
            name = "old.jpg"
            ressType = RessType.TN.i
            this.storage = storage
        } else null

        val mediumId = nextMediumId++
        val medium = Medium().apply {
            id = mediumId
            // named after the file, the way LocationService.draftMset names a scanned medium, so a
            // batch failure reports something recognisable rather than an opaque id
            name = primaryName.substringAfterLast('/')
            mtype = 2
            this.bessources = (
                (if (registerPrimary) listOf(primary) else emptyList()) +
                    extraBessources +
                    listOfNotNull(staleTn)
                ).toMutableList()
        }
        medium.bessources.forEach { it.medium = medium }
        whenever(mediaRepository.findById(medium.id!!)).thenReturn(Optional.of(medium))

        if (createPrimaryFile) {
            val source = File(mainFs.uri, primaryName)
            source.parentFile.mkdirs()
            when {
                primaryContent != null -> source.writeText(primaryContent)
                image != null -> ImageIO.write(image, imageFormat, source)
                else -> ImageIO.write(image(800, 600), imageFormat, source)
            }
        }

        return Fixture(medium.id!!, storage, mainFs, tnFs, primary, medium, staleTn)
    }

    /**
     * An mset holding one medium per given [primaryNames], registered with the repository under
     * [MSET_ID] and answerable through the join fetch query the service reads.
     *
     * Every medium gets a storage of its own, which is what a real scanned mset normally looks like
     * and what keeps the per medium assertions independent of each other.
     *
     * A null name stands for a medium with no primary bessource at all, the state migrated media of
     * type (legacy) folder are in. [createFilesFor] limits which media actually get a file on disk,
     * and [primaryWithoutStorageFor] / [tnFsMissingFor] knock out one precondition for the media at
     * those positions, so a batch test can make exactly one medium unthumbnailable.
     */
    private fun givenMsetOf(
        vararg primaryNames: String?,
        createFilesFor: Set<Int> = primaryNames.indices.toSet(),
        primaryWithoutStorageFor: Set<Int> = emptySet(),
        tnFsMissingFor: Set<Int> = emptySet()
    ): List<Fixture> {
        val fixtures = primaryNames.mapIndexed { index, name ->
            givenMedium(
                primaryName = name ?: "medium-$index",
                createPrimaryFile = index in createFilesFor,
                registerPrimary = name != null,
                primaryWithoutStorage = index in primaryWithoutStorageFor,
                tnFsExistsAsLocation = index !in tnFsMissingFor
            )
        }

        val mset = Mset().apply {
            id = MSET_ID
            name = "mset-$MSET_ID"
            media = fixtures.map { it.medium }.toMutableList()
        }
        whenever(msetRepository.findByIdOrNullWithMedia(MSET_ID)).thenReturn(mset)

        return fixtures
    }

    private fun location(
        uri: String,
        locationType: LocationType,
        storage: Storage,
        inuse: Byte = 1
    ): Location = Location().apply {
        name = uri
        this.uri = uri
        this.locationType = locationType.i
        this.inuse = inuse
        this.storage = storage
    }

    private fun primaryBessource(name: String, storage: Storage): Bessource = Bessource().apply {
        id = 1
        this.name = name
        ressType = RessType.PRIMARY.i
        this.storage = storage
    }

    private fun image(width: Int, height: Int, color: Color = Color.BLUE): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            try {
                g.color = color
                g.fillRect(0, 0, width, height)
            } finally {
                g.dispose()
            }
        }
}