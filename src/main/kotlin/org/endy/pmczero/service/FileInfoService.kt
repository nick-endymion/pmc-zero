package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.FileInfo
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.FileInfoRepository
import org.endy.pmczero.to.FileInfoFailureTO
import org.endy.pmczero.to.FileInfoRunTO
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
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
 * this is asked for per bessource or over a whole storage at once, and a bessource with no row is a
 * bessource nobody has looked at.
 *
 * The whole file is read to hash it, so this is a call for a caller that means it: one row, one
 * gallery of them, or one storage of them, rather than a scan of a location on the way past. A storage
 * is the expensive one and the one that needs a batch, see [recordMissingOf].
 */
@Service
class FileInfoService(
    private val fileInfoRepository: FileInfoRepository,
    private val bessourceRepository: BessourceRepository,
    private val bessourceFiles: BessourceFiles,
    transactionManager: PlatformTransactionManager
) {

    /**
     * The transaction one batch of [recordMissingOf] is written in.
     *
     * REQUIRES_NEW rather than the default, so that a batch really is committed on its own: with the
     * default and a caller that has a transaction of its own, every batch would join that one and the
     * commit that was asked for every hundred files would be the caller's commit at the end.
     *
     * A template and not a method of this class annotated with `@Transactional`, since a call from
     * within the class to an annotated method of the same class is a plain call: the annotation is
     * read off the proxy, and there is no proxy on the inside of one.
     */
    private val batch = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    private val logger = LoggerFactory.getLogger(FileInfoService::class.java)

    /**
     * Records what is missing of [storageId]: the file behind every bessource of that storage that has
     * no row yet, and answers what the run did, see [FileInfoRunTO].
     *
     * A bessource with a row is left alone rather than recorded again. That is what makes this worth
     * calling over a whole storage: a file that was looked at once is not looked at again, and a
     * storage of a hundred thousand files costs a hundred thousand hashes the first time and none at
     * all afterwards. It also means the row is believed: a file replaced since the row was written
     * keeps the size and hash of the file it was, and a caller that wants that noticed asks for those
     * files by name rather than asking for everything again.
     *
     * Every bessource of the storage is a candidate, of whatever ressource type. A thumbnail is a file
     * this application manages and [recordFor] knows where to find it, and a bessource of type URL has
     * no file on disk at all, which is reported as a failure rather than passed over in silence.
     *
     * The writes of a hundred files land together and are committed together, see [COMMIT_EVERY].
     * Every file is attempted on its own and its failure reported separately, so one unreadable file
     * does not undo the ninety-nine beside it in its batch, and a storage that is half unreachable
     * still gets the half that is not.
     *
     * @throws org.endy.pmczero.exception.NotFoundException when no storage has that id
     */
    fun recordMissingOf(storageId: Int): FileInfoRunTO {
        val alreadyRecorded = fileInfoRepository.findBessourceIdsOfStorage(storageId).toSet()
        val missing = bessourceRepository.findIdsOfStorage(storageId).filterNot { it in alreadyRecorded }

        var recorded = 0
        var failed = 0
        val failures = mutableListOf<FileInfoFailureTO>()

        for (bessourceIds in missing.chunked(COMMIT_EVERY)) {
            recorded += batch.execute {
                bessourceIds.count { bessourceId ->
                    try {
                        recordFor(bessourceId)
                        true
                    } catch (e: Throwable) {
                        // Throwable and not Exception, because the exceptions of this application
                        // extend Throwable rather than Exception, see NotFoundException: a catch of
                        // Exception would let the first unreadable file end the run it was meant to
                        // be reported in. An Error is not one of those and is not caught here: an out
                        // of memory while hashing a file is the jvm saying it cannot go on, and
                        // carrying on with the next thousand files would only bury that.
                        if (e is Error) throw e

                        failed++
                        logger.warn("could not record the file of bessource $bessourceId: ${e.message}")
                        if (failures.size < MAX_FAILURES_REPORTED) {
                            failures.add(FileInfoFailureTO(bessourceId, e))
                        }
                        false
                    }
                }
            }!!
        }

        return FileInfoRunTO(
            storageId = storageId,
            attempted = missing.size,
            recorded = recorded,
            skipped = alreadyRecorded.size,
            failed = failed,
            failures = failures
        )
    }

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

    companion object {
        /**
         * The algorithm of [FileInfo.hash], named here so that the entity and this agree without one
         * having to read the other's comment.
         */
        const val HASH_ALGORITHM = "SHA-256"

        /** how much of a file is read at a time, since the whole of it is not worth holding */
        const val BLOCK_SIZE = 64 * 1024

        /**
         * how many files [recordMissingOf] writes before it commits them, see its own comment
         *
         * A constant rather than a parameter, since it is a property of writing rather than of the
         * storage: a smaller number means more commits over the same work, a larger one means more work
         * undone by whatever goes wrong near the end.
         */
        const val COMMIT_EVERY = 100

        /**
         * how many failures [recordMissingOf] names in its answer, see [FileInfoRunTO.failures]
         *
         * A cap rather than all of them, since a storage whose location is unreachable fails every file
         * on it and an answer that names fifty thousand of them is not an answer anybody can read. The
         * count in [FileInfoRunTO.failed] is the real number either way.
         */
        const val MAX_FAILURES_REPORTED = 50
    }
}
