package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.to.BessourceTO
import org.endy.pmczero.to.MsetThumbnailsTO
import org.endy.pmczero.to.ThumbnailFailureTO
import org.endy.pmczero.to.ThumbnailTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Creates the thumbnail of a medium and records it as a TN bessource.
 *
 * The file is written into the [LocationType.TN_FS] location of the storage the primary bessource
 * lives on, mirroring the subdirectory of the primary file: a medium whose primary bessource is
 * `tn/2020/shot.jpg` on the MAIN_FS location gets its thumbnail at `tn/2020/shot.jpg` inside the
 * TN_FS location, and any directory on the way that does not exist yet is created. A TN bessource is
 * then pointed at the new file, so [MediaService.ressourceUrls] stops serving a url derived from the
 * primary for a thumbnail that is now really there.
 *
 * Thumbnail generation is deliberately not part of [LocationService.providePhysicalRessources]. That
 * function derives urls from the database and runs on every read, including reads that only want the
 * url of a medium whose files are gone. Writing an image to disk is an expensive, side-effecting
 * one-off, so it belongs behind an explicit call rather than in the read path.
 */
@Service
class ThumbnailService(
    private val mediaRepository: MediaRepository,
    private val bessourceRepository: BessourceRepository,
    private val storageService: StorageService,
    private val locationService: LocationService,
    // for the batch call only; nothing here is needed by the single medium path
    private val msetService: MsetService
) {

    /**
     * Creates the thumbnail of the medium with the given [id] and points its TN bessource at it.
     *
     * An existing thumbnail file is left alone unless [force] is true, so calling this over a whole
     * set is cheap and repeatable: only the media that really need one are processed. The bessource
     * is written either way, because a file on disk that no bessource points at is not reachable
     * through any url, and repairing that costs a single row.
     *
     * @param force regenerate the thumbnail even when the target file is already there
     * @throws NotFoundException when no medium with that id exists
     * @throws NotAccessibleException when the medium cannot be thumbnailed: it has no primary
     * bessource, its storage has no in use MAIN_FS or TN_FS location, the primary file is missing or
     * unreadable, it is not an image, the thumbnail directory cannot be created, or the thumbnail
     * cannot be written
     */
    @Transactional
    fun createThumbnail(id: Int, force: Boolean = false): ThumbnailTO {
        val medium = mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()

        val primary = primaryOf(medium)
        val storage = primary.storageOrNull()
            ?: throw NotAccessibleException("the primary bessource of medium $id has no storage")
        val storageId = storage.id
            ?: throw NotAccessibleException("medium $id sits on a storage without id")

        val mainFs = storage.locationInUse(LocationType.MAIN_FS.i)
            ?: throw NotAccessibleException("storage $storageId has no MAIN_FS location in use")
        val tnFs = storage.locationInUse(LocationType.TN_FS.i)
            ?: throw NotAccessibleException("storage $storageId has no TN_FS location in use")

        // resolved once, so the source path and the thumbnail path are built from the same name
        val primaryName = primary.name
            ?: throw NotAccessibleException("the primary bessource of medium $id has no name")

        val source = fileIn(mainFs, primaryName)
            ?: throw NotAccessibleException("the primary bessource of medium $id escapes its MAIN_FS location")
        if (!source.isFile || !source.canRead())
            throw NotAccessibleException("the primary file ${source.path} of medium $id is missing or unreadable")

        val relative = thumbnailName(primaryName)
        val target = fileIn(tnFs, relative)
            ?: throw NotAccessibleException("the thumbnail path of medium $id escapes its TN_FS location")

        createParentDirectory(target, id)

        // re-checked after the directory exists: another writer may have created the file between
        // the first check and the mkdirs
        val created = force || !target.isFile
        val image = if (created) write(target, source) else ImageIO.read(target)
            ?: throw NotAccessibleException("the existing thumbnail ${target.path} is not a readable image")

        saveBessource(medium, storage, relative)

        return ThumbnailTO(
            mediumId = id,
            name = relative,
            url = locationService.url(BessourceTO(name = relative, ressType = RessType.TN.i), tnFs),
            width = image.width,
            height = image.height,
            created = created
        )
    }

    /**
     * Creates the directory [target] lives in, so a TN location that was never populated gets its
     * folders on the first call.
     *
     * `mkdirs` returning false is not on its own a failure: it also returns false when the
     * directory turned out to exist already, so the `isDirectory` check is what distinguishes the
     * two.
     */
    private fun createParentDirectory(target: File, mediumId: Int) {
        val parent = target.parentFile
            ?: throw NotAccessibleException("the thumbnail path of medium $mediumId has no directory")

        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory)
            throw NotAccessibleException("could not create the thumbnail directory ${parent.path}")
    }

    /**
     * Creates the thumbnail of every medium of the mset with the given [id], answering what happened
     * to each of them.
     *
     * One medium that cannot be thumbnailed does not stop the others. An mset holds media from a
     * scan, so it routinely contains media that have no primary bessource at all (migrated media of
     * type (legacy) folder), media whose files have since been removed, and media of a type that is
     * not an image. Failing the whole call on the first of those would make the endpoint useless for
     * exactly the sets that need it most, so each medium is attempted on its own and its outcome is
     * reported separately.
     *
     * @param force regenerate every thumbnail, even those that are already there
     * @throws NotFoundException when no mset with that id exists
     */
    @Transactional
    fun createThumbnailsOfMset(id: Int, force: Boolean = false): MsetThumbnailsTO {
        // withMedia: the media are the point of the call, and reading them lazily one by one would
        // mean a query per medium
        val media = msetService.findById(id, withMedia = true).media

        val thumbnails = mutableListOf<ThumbnailTO>()
        val skipped = mutableListOf<Int>()
        val failures = mutableListOf<ThumbnailFailureTO>()

        for (medium in media) {
            val mediumId = medium.id ?: continue

            // a medium without a primary bessource has nothing to derive a thumbnail from, which is
            // a normal state rather than a fault, so it is counted apart from the real failures
            if (medium.bessources.none { it.ressType == RessType.PRIMARY.i }) {
                skipped.add(mediumId)
                continue
            }

            try {
                thumbnails.add(createThumbnail(mediumId, force))
            } catch (e: Throwable) {
                // Throwable rather than Exception on purpose: NotAccessibleException and
                // NotFoundException both extend Throwable directly, so catching Exception would let
                // either of them abort the whole batch. They are also deliberately not rethrown,
                // because one unthumbnailable medium is not a failure of the call.
                failures.add(ThumbnailFailureTO(mediumId, medium.name, e.message ?: e.toString()))
            }
        }

        return MsetThumbnailsTO(
            msetId = id,
            total = media.size,
            created = thumbnails.count { it.created },
            unchanged = thumbnails.count { !it.created },
            failed = failures.size,
            thumbnails = thumbnails,
            skipped = skipped,
            failures = failures
        )
    }

    /**
     * Writes the scaled-down [source] to [target] as a JPEG and answers what was written.
     *
     * Encoded into a temporary file next to the target and moved into place, so a failure part way
     * through the encode cannot leave a truncated file where a thumbnail is expected. The thumbnail
     * is served right after by [MediaService.ressourceUrls], and a half-written JPEG would fail
     * there with a far less obvious error than the one raised here.
     *
     * @throws NotAccessibleException when [source] cannot be read as an image, no JPEG writer is
     * available, or the temporary file cannot be written or moved into place
     */
    private fun write(target: File, source: File): BufferedImage {
        val original = ImageIO.read(source)
            ?: throw NotAccessibleException("${source.path} is not an image that can be read")

        val thumbnail = scale(original)
        val temp = Files.createTempFile(target.parentFile.toPath(), "${target.name}.", ".tmp").toFile()

        try {
            if (!ImageIO.write(thumbnail, "jpg", temp))
                throw NotAccessibleException("no JPEG writer is available for ${temp.path}")
            // via Files.move rather than File.renameTo, which refuses to replace an existing file on
            // windows, so a forced regeneration of an existing thumbnail would fail there
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw NotAccessibleException("could not write the thumbnail to ${target.path}: ${e.message}")
        } finally {
            // a no-op on the success path, where the file has been moved away, and the cleanup that
            // keeps a failed attempt from leaving a stray temp file in the TN location
            temp.delete()
        }

        return thumbnail
    }

    /**
     * Scales [image] down so its longest edge is at most [maxEdge], keeping the aspect ratio.
     *
     * An image that already fits is returned untouched: upscaling a small file costs bytes and adds
     * no detail, so the original is the better thumbnail.
     *
     * Drawn onto a [BufferedImage.TYPE_INT_RGB] canvas rather than scaled in place, because the
     * common source formats carry an alpha channel that JPEG cannot store. Scaling such an image in
     * place and writing it as JPEG turns every transparent area black; painting it onto an opaque
     * white canvas is what makes a transparent PNG come out with a white background instead.
     */
    private fun scale(image: BufferedImage): BufferedImage {
        val longest = max(image.width, image.height)
        if (longest <= maxEdge) return image

        val factor = maxEdge.toDouble() / longest
        val width = max(1, (image.width * factor).roundToInt())
        val height = max(1, (image.height * factor).roundToInt())

        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = scaled.createGraphics()
        try {
            // bilinear rather than the default nearest neighbour, which drops source pixels instead
            // of averaging them and leaves visible aliasing on a downscaled photo
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.drawImage(image, 0, 0, width, height, null)
        } finally {
            // without this the canvas stays locked and each call leaks native memory
            graphics.dispose()
        }

        return scaled
    }

    /**
     * Points the TN bessource of [medium] at [relative] on [storage], replacing the one it has.
     *
     * A medium has at most one TN bessource: [LocationService.providePhysicalRessources] only
     * derives a thumbnail from the primary when the medium has none, so a second row would be
     * unreachable. An existing row is therefore updated rather than added to, which also keeps its
     * id stable for anything already referring to it.
     */
    private fun saveBessource(medium: Medium, storage: Storage, relative: String) {
        val existing = medium.bessources.firstOrNull { it.ressType == RessType.TN.i }

        if (existing != null) {
            existing.name = relative
            // the primary's storage, not the TN location's: a bessource points at a storage, and the
            // TN location is only a place on that storage
            existing.storage = storage
            bessourceRepository.save(existing)
            return
        }

        val created = Bessource().apply {
            name = relative
            ressType = RessType.TN.i
            this.medium = medium
            this.storage = storage
        }
        bessourceRepository.save(created)
        // kept on the in-memory medium too, so a caller reusing the medium it passed in sees the new
        // bessource without re-reading it
        medium.bessources.add(created)
    }

    /** The primary bessource of [medium], which is the file a thumbnail is made from. */
    private fun primaryOf(medium: Medium): Bessource = medium.bessources
        .firstOrNull { it.ressType == RessType.PRIMARY.i }
        ?: throw NotAccessibleException(
            "medium ${medium.id} has no primary bessource, so there is nothing to make a thumbnail from"
        )

    /**
     * The name of the thumbnail of a primary bessource named [primaryName].
     *
     * The subdirectory is kept, so the thumbnail sits below the same relative path inside the TN
     * location as the original sits below the main one. Without that, two media with the same file
     * name in different subdirectories would fight over one thumbnail file.
     *
     * Only the extension of the last segment is replaced, and it always becomes `.jpg`, because the
     * thumbnail is written as a JPEG. Leaving e.g. `.png` on JPEG bytes would make the stored name
     * claim a format the file is not.
     *
     * Separators are normalised to `/`, which is what a bessource name holds: it is built from
     * location relative paths, which are `/` separated on every platform. A name that arrived with
     * `\` separators would otherwise become part of a single file name on a posix system rather
     * than directory boundaries.
     */
    private fun thumbnailName(primaryName: String): String {
        val normalised = primaryName.replace('\\', '/')
        val directory = normalised.substringBeforeLast('/', "")
        val fileName = normalised.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)

        return if (directory.isEmpty()) "$stem.jpg" else "$directory/$stem.jpg"
    }

    /**
     * [relative] inside the folder of the FS [location], null when it would escape that folder.
     *
     * Canonicalised before the comparison, which is what rules out `..` and symlinks: a bessource
     * name is free text, and a generated thumbnail must never land outside the location it was
     * assigned to.
     */
    private fun fileIn(location: Location, relative: String?): File? {
        val root = File(location.uri ?: return null).canonicalFile
        val file = File(root, relative ?: return null).canonicalFile

        return if (file.path.startsWith(root.path + File.separator)) file else null
    }

    /** The longest edge of a generated thumbnail, in pixels. */
    private val maxEdge = 512
}