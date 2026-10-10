package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.FileInfo
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.FileInfoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Records what the file behind a bessource is like on disk, see [FileInfo].
 *
 * Nothing records a row on the way in: a scan writes its files and moves on, and hashing every image
 * of every gallery to fill a column nobody asked for is work that only pays off when somebody asks. So
 * this is asked for per bessource, and a bessource with no row is a bessource nobody has looked at.
 *
 * The whole file is read to hash it, so this is a call for a caller that means it: one row, or one
 * gallery of them, rather than a scan of a location on the way past.
 */
@Service
class FileInfoService(
    private val fileInfoRepository: FileInfoRepository,
    private val bessourceRepository: BessourceRepository,
    private val bessourceFiles: BessourceFiles
) {

    /**
     * Determines size, timestamps and hash of the file behind bessource [bessourceId] and stores them,
     * answering the row.
     *
     * A bessource that has a row already is updated rather than added to: the row is a fact about the
     * file as it is now, and a file that has been replaced since the last look should not keep the
     * answers of the one it was. So calling this twice over a file that did not change changes nothing
     * but the row's own id, and calling it after a change is how a caller notices one.
     *
     * The hash is computed by reading the file, so a large file is read in full. A file that is being
     * written while it is hashed is hashed as far as it was read, which is the size and the hash of that
     * and not of the finished file; a caller that needs that to be true has to record it again
     * afterwards, which is what this being callable again is for.
     *
     * @throws org.endy.pmczero.exception.NotFoundException when no bessource has that id
     * @throws NotAccessibleException when the file behind the bessource cannot be found or read: no
     * folder on disk for its ressource type, a name that does not stay inside that folder, a file that
     * is not there, or one that cannot be read
     */
    fun recordFor(bessourceId: Int): FileInfo {
        val bessource = bessourceRepository.findByIdOrNull(bessourceId) ?: throw NotFoundException()

        val file = bessourceFiles.fileOf(bessource)
            ?: throw NotAccessibleException(
                "bessource $bessourceId points at no file on disk, so it has no size, no timestamps " +
                    "and no hash"
            )

        val attributes = attributesOf(file, bessourceId)

        val info = fileInfoRepository.findByBessourceId(bessourceId) ?: FileInfo()
        info.bessourceId = bessourceId
        info.size = attributes.size
        info.fileCreatedAt = attributes.fileCreatedAt
        info.fileChangedAt = attributes.fileChangedAt
        info.hash = hashOf(file, bessourceId)

        return fileInfoRepository.save(info)
    }

    /**
     * The row of bessource [bessourceId], or null when nobody has recorded one.
     *
     * A read that answers null rather than a row of zeroes, so a caller can tell "not looked at" from
     * "looked at and the file was empty".
     */
    fun findByBessourceId(bessourceId: Int): FileInfo? = fileInfoRepository.findByBessourceId(bessourceId)

    /**
     * What the file system says about [file], as a [LocalDateTime] rather than an instant.
     *
     * Read through [java.nio.file.attribute.BasicFileAttributes] rather than through [File], since
     * that is where a creation time is: [File.lastModified] is the only timestamp the older api has,
     * and it is the changed one.
     *
     * A file system that keeps no creation time answers null for it rather than something made up, so
     * a row never claims a file was created at the epoch.
     */
    private fun attributesOf(file: File, bessourceId: Int): Attributes = try {
        val attributes = Files.readAttributes(file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java)

        Attributes(
            size = attributes.size(),
            fileCreatedAt = attributes.creationTime()?.toLocal(),
            fileChangedAt = attributes.lastModifiedTime()?.toLocal()
        )
    } catch (e: IOException) {
        throw NotAccessibleException("the file of bessource $bessourceId is not readable: ${e.message}")
    }

    /** what [attributesOf] answers, so the three are read once for one file */
    private data class Attributes(
        val size: Long,
        val fileCreatedAt: LocalDateTime?,
        val fileChangedAt: LocalDateTime?
    )

    /**
     * The SHA-256 of the content of [file], in lower case hex.
     *
     * Streamed rather than read into memory: the files of this application are images, but nothing says
     * a bessource cannot point at a video, and a hash that needs the whole file in memory is a limit on
     * what may be recorded.
     */
    private fun hashOf(file: File, bessourceId: Int): String = try {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)

        file.inputStream().use { stream -> digest.readInBlocksOf(stream) }

        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: IOException) {
        throw NotAccessibleException("the file of bessource $bessourceId could not be read: ${e.message}")
    } catch (e: NoSuchAlgorithmException) {
        // every jvm is required to have sha-256, so this says the jvm is broken rather than that a
        // caller did anything wrong
        throw IllegalStateException("$HASH_ALGORITHM is not available on this jvm", e)
    }

    /** feeds [stream] through [this] in blocks, since a whole file is not worth holding */
    private fun MessageDigest.readInBlocksOf(stream: InputStream) {
        val buffer = ByteArray(BLOCK_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) return
            update(buffer, 0, read)
        }
    }

    private fun java.nio.file.attribute.FileTime?.toLocal(): LocalDateTime? =
        this?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()

    private companion object {
        /**
         * The algorithm of [FileInfo.hash], named here so that the entity and this agree without one
         * having to read the other's comment.
         */
        const val HASH_ALGORITHM = "SHA-256"

        /** how much of a file is read at a time, since the whole of it is not worth holding */
        const val BLOCK_SIZE = 64 * 1024
    }
}
