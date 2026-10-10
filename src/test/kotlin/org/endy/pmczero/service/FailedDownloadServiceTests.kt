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
 * Unit tests for [FailedDownloadService], against real files in a temp folder and a fetcher that
 * writes rather than reaching the network.
 *
 * What matters is what happens to the cache: a download that worked is gone from it and a download
 * that failed again is still in it with one more attempt, since that is what makes calling again
 * worth anything.
 */
class FailedDownloadServiceTests {

    @BeforeEach
    fun setUp() {
        FailedDownloads.clear()
        fetcher = WritingFetcher()
        service = FailedDownloadService()
    }

    @TempDir
    lateinit var tempDir: File

    private lateinit var fetcher: WritingFetcher
    private lateinit var service: FailedDownloadService

    // -------------------------------------------------------------------------------------
    // What a retry writes
    // -------------------------------------------------------------------------------------

    @Test
    fun `writes the file of a download that failed`() {
        givenDownload("http://example.org/a.jpg", "2020/a.jpg")

        val run = service.retryAll()

        assertEquals(1, run.attempted)
        assertEquals(1, run.downloaded)
        assertEquals(0, run.failed)
        assertEquals("content of http://example.org/a.jpg", File(tempDir, "2020/a.jpg").readText())
    }

    /**
     * Over the fetcher the download failed with, since a failure that needs the session of a browser is
     * not fixed by asking a plain http client.
     */
    @Test
    fun `fetches over the fetcher the download failed with`() {
        givenDownload("http://example.org/a.jpg")

        service.retryAll()

        assertEquals(listOf("http://example.org/a.jpg"), fetcher.downloads.map { it.url })
    }

    /** No proxy, which is what the download that failed used. */
    @Test
    fun `fetches without a proxy`() {
        givenDownload("http://example.org/a.jpg")

        service.retryAll()

        assertEquals(listOf(false), fetcher.downloads.map { it.withProxy })
    }

    /** The path the scan decided on, which may be a variant a name of the medium would not produce. */
    @Test
    fun `writes to the path the failed download had`() {
        givenDownload("http://example.org/one/a.jpg", "2020/a.1.jpg")

        service.retryAll()

        assertEquals(listOf("2020/a.1.jpg"), written(), "the variant, and not a second a.jpg")
    }

    // -------------------------------------------------------------------------------------
    // What it does to the cache
    // -------------------------------------------------------------------------------------

    /** Dropped, so a second call has nothing to do for it. */
    @Test
    fun `forgets a download it fetched`() {
        givenDownload("http://example.org/a.jpg")

        service.retryAll()

        assertEquals(emptyList(), FailedDownloads.all())
    }

    @Test
    fun `keeps a download that failed again`() {
        givenDownload("http://example.org/a.jpg")
        fetcher.failing = true

        service.retryAll()

        assertEquals(1, FailedDownloads.count())
    }

    /** One more attempt on it, which is what tells a file that is not there from one that timed out. */
    @Test
    fun `counts the attempt of a download that failed again`() {
        givenDownload("http://example.org/a.jpg")
        fetcher.failing = true

        service.retryAll()

        assertEquals(2, FailedDownloads.all().single().attempts)
    }

    /** The reason of this attempt, since that is the one worth reading. */
    @Test
    fun `keeps the reason of the attempt that failed again`() {
        givenDownload("http://example.org/a.jpg", reason = "timed out")
        fetcher.failing = true

        service.retryAll()

        assertEquals("download of http://example.org/a.jpg refused", FailedDownloads.all().single().reason)
    }

    /**
     * A file somebody fetched in the meantime, by hand or by another scrape: fetching it again would
     * replace a file a medium may have been recorded against.
     */
    @Test
    fun `skips a download whose file is there already`() {
        givenDownload("http://example.org/a.jpg")
        File(tempDir, "a.jpg").writeText("fetched in the meantime")

        val run = service.retryAll()

        assertEquals(1, run.attempted)
        assertEquals(0, run.downloaded)
        assertEquals(1, run.skipped)
        assertEquals(emptyList(), fetcher.downloads, "nothing was fetched")
        assertEquals("fetched in the meantime", File(tempDir, "a.jpg").readText(), "it was left alone")
    }

    @Test
    fun `forgets a download whose file is there already`() {
        givenDownload("http://example.org/a.jpg")
        File(tempDir, "a.jpg").writeText("fetched in the meantime")

        service.retryAll()

        assertEquals(emptyList(), FailedDownloads.all())
    }

    // -------------------------------------------------------------------------------------
    // What it answers
    // -------------------------------------------------------------------------------------

    /** Nothing waiting is nothing to do, which is not a failure and not an error. */
    @Test
    fun `answers an empty run when the cache is empty`() {
        val run = service.retryAll()

        assertEquals(0, run.attempted)
        assertEquals(0, run.downloaded)
        assertEquals(0, run.skipped)
        assertEquals(0, run.failed)
    }

    /** One download that cannot be had does not stop the ones beside it. */
    @Test
    fun `carries on after a download that fails again`() {
        givenDownload("http://example.org/one.jpg", "one.jpg")
        givenDownload("http://example.org/two.jpg", "two.jpg")
        fetcher.failingFor = setOf("http://example.org/one.jpg")

        val run = service.retryAll()

        assertEquals(2, run.attempted)
        assertEquals(1, run.downloaded)
        assertEquals(1, run.failed)
        assertEquals(listOf("two.jpg"), written(), "the file of the one that worked and of no other")
    }

    /** A reason on its own does not say which of two hundred files it was. */
    @Test
    fun `names the url and the path of a download that failed again`() {
        givenDownload("http://example.org/one.jpg", "2020/one.jpg")
        fetcher.failing = true

        val run = service.retryAll()

        val failure = run.failures.single()
        assertEquals("http://example.org/one.jpg", failure.url)
        assertTrue(failure.target.endsWith("2020${File.separator}one.jpg"), "it was ${failure.target}")
        assertEquals(2, failure.attempts)
        assertTrue(failure.reason.isNotBlank())
    }

    /** A gallery of two hundred that timed out is a gallery of two hundred that timed out. */
    @Test
    fun `names no more failures than it reports`() {
        for (i in 1..60) {
            givenDownload("http://example.org/$i.jpg", "$i.jpg")
        }
        fetcher.failing = true

        val run = service.retryAll()

        assertEquals(60, run.failed, "every one of them failed again")
        assertEquals(FailedDownloadService.MAX_FAILURES_REPORTED, run.failures.size)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a failed download of [url] into [path] below the temp folder, waiting in the cache */
    private fun givenDownload(url: String, path: String = url.substringAfterLast('/'), reason: String = "timed out") {
        FailedDownloads.remember(
            FailedDownload(
                url = url,
                target = File(tempDir, path),
                fetcher = fetcher,
                reason = reason,
                failedAt = LocalDateTime.now(),
                attempts = 1
            )
        )
    }

    /** the paths of the files below the temp folder, `/` separated */
    private fun written(): List<String> =
        tempDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(tempDir).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

    private data class Download(val url: String, val target: File, val withProxy: Boolean)

    /**
     * A [Fetcher] that writes a marker file instead of making a request, and records what it was asked
     * for, so that a retry can be tested without a server.
     */
    private class WritingFetcher : Fetcher {

        val downloads = mutableListOf<Download>()

        /** makes every download fail, to check that a failure is reported rather than swallowed */
        var failing = false

        /** makes only these fail, for a run that is partly hopeless */
        var failingFor = emptySet<String>()

        override fun getAsString(urlString: String, withProxy: Boolean): String = ""

        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {
            downloadTo(urlString, File(directory, "downloaded"))
        }

        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            if (failing || urlString in failingFor) throw NotAccessibleException("download of $urlString refused")

            downloads.add(Download(urlString, target, withProxy))
            target.parentFile?.mkdirs()
            target.writeText("content of $urlString")
            return target
        }
    }
}