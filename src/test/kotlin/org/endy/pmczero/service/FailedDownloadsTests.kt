package org.endy.pmczero.service

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.FailedDownload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [FailedDownloads], the cache the failed downloads are kept in.
 *
 * A cache is mostly about what it does with the same thing twice, so most of these hand it one entry
 * twice and ask whether there is one entry or two.
 */
class FailedDownloadsTests {

    @BeforeEach
    fun setUp() {
        FailedDownloads.clear()
    }

    @TempDir
    lateinit var tempDir: File

    private val fetcher = FailingFetcher()

    @Test
    fun `keeps a download that failed`() {
        val download = givenDownload("http://example.org/a.jpg")

        FailedDownloads.remember(download)

        assertEquals(listOf(download), FailedDownloads.all())
        assertEquals(1, FailedDownloads.count())
    }

    @Test
    fun `keeps nothing for a download that has not failed`() {
        assertEquals(emptyList(), FailedDownloads.all())
        assertEquals(0, FailedDownloads.count())
    }

    /**
     * The same url into the same file is the same download, so a second failure is one entry with one
     * more attempt rather than a second entry to be retried and counted twice.
     */
    @Test
    fun `counts a download that failed again instead of keeping a second one`() {
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg"))
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg"))

        assertEquals(1, FailedDownloads.count())
        assertEquals(2, FailedDownloads.all().single().attempts)
    }

    /** One url is fetched into a different file for every scan, so those are two downloads. */
    @Test
    fun `keeps the same url for two different files apart`() {
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg", "2020/a.jpg"))
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg", "2021/a.jpg"))

        assertEquals(2, FailedDownloads.count())
        assertEquals(1, FailedDownloads.all().map { it.attempts }.distinct().single())
    }

    /** A run that fails on a hundred images is a hundred entries, so every one of them is retried. */
    @Test
    fun `keeps every download of a run apart`() {
        for (i in 1..100) {
            FailedDownloads.remember(givenDownload("http://example.org/$i.jpg", "2020/$i.jpg"))
        }

        assertEquals(100, FailedDownloads.count())
    }

    /** The newer reason is the one worth reading, since it is the one from the last attempt. */
    @Test
    fun `keeps the reason of the last failure`() {
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg", reason = "timed out"))
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg", reason = "connection reset"))

        assertEquals("connection reset", FailedDownloads.all().single().reason)
    }

    /** The entry of the first attempt, and one attempt on it, so a caller can tell an old failure. */
    @Test
    fun `counts the attempts of an entry it answers`() {
        val download = givenDownload("http://example.org/a.jpg")

        assertNull(FailedDownloads.find(download), "nothing kept yet")

        FailedDownloads.remember(download)

        assertEquals(download, FailedDownloads.find(download))
    }

    @Test
    fun `forgets a download it has fetched`() {
        val download = givenDownload("http://example.org/a.jpg")
        FailedDownloads.remember(download)

        assertTrue(FailedDownloads.forget(download))
        assertEquals(emptyList(), FailedDownloads.all())
    }

    /** Nothing to forget is not a failure: a caller forgetting twice has done what it asked. */
    @Test
    fun `forgets nothing twice`() {
        val download = givenDownload("http://example.org/a.jpg")
        FailedDownloads.remember(download)

        FailedDownloads.forget(download)

        assertEquals(false, FailedDownloads.forget(download))
    }

    /**
     * By key and not by equality, so a caller holding the entry it started with still removes the one
     * that has one more attempt on it now.
     */
    @Test
    fun `forgets a download an older copy of it stands for`() {
        val download = givenDownload("http://example.org/a.jpg")
        FailedDownloads.remember(download)
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg", reason = "again"))

        FailedDownloads.forget(download)

        assertEquals(emptyList(), FailedDownloads.all())
    }

    @Test
    fun `empties itself when asked to`() {
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg"))

        FailedDownloads.clear()

        assertEquals(0, FailedDownloads.count())
    }

    /** A snapshot, since a retry works over it while new failures may come in beside it. */
    @Test
    fun `answers a snapshot of what was kept`() {
        FailedDownloads.remember(givenDownload("http://example.org/a.jpg"))
        val answer = FailedDownloads.all()

        FailedDownloads.remember(givenDownload("http://example.org/b.jpg"))

        assertEquals(1, answer.size, "the answer was taken when it was asked for")
        assertEquals(2, FailedDownloads.count())
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a failed download of [url] into [path] below the temp folder, with [reason] as its reason */
    private fun givenDownload(
        url: String,
        path: String = "a.jpg",
        reason: String = "timed out"
    ) = FailedDownload(
        url = url,
        target = File(tempDir, path),
        fetcher = fetcher,
        reason = reason,
        failedAt = LocalDateTime.now(),
        attempts = 1
    )

    /** only ever a fetcher here: nothing in this suite downloads anything */
    private class FailingFetcher : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean): String = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {
            throw NotAccessibleException("nothing downloads in this suite")
        }

        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            throw NotAccessibleException("nothing downloads in this suite")
        }
    }
}