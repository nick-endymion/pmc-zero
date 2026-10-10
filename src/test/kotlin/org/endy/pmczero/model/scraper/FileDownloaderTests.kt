package org.endy.pmczero.model.scraper

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Fetcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FileDownloader], against a [Fetcher] that records what it was asked for instead of
 * going near the network.
 *
 * The point of every test here is the path a file lands at rather than its content: the fetcher
 * writes a marker, so a test can tell which file was written and that it was written at all without
 * standing up a server.
 */
class FileDownloaderTests {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fetcher: RecordingFetcher
    private lateinit var worker: FileDownloader

    /** the folder the scan writes into, i.e. the MAIN_FS location */
    private lateinit var location: Location

    @BeforeEach
    fun setUp() {
        fetcher = RecordingFetcher()
        worker = FileDownloader()
        location = givenLocation(tempDir.absolutePath, LocationType.MAIN_FS)
    }

    // -------------------------------------------------------------------------------------
    // Where the file lands
    // -------------------------------------------------------------------------------------

    @Test
    fun `writes the file into the location root when the kontext has no path`() {
        val kontext = kontextFor(location, "")

        worker.applya("http://example.org/bilder/erstes.jpg", kontext)

        assertEquals(listOf("erstes.jpg"), writtenRelativeTo(location))
    }

    @Test
    fun `writes the file into the folder the kontext names`() {
        val kontext = kontextFor(location, "2020/august")

        worker.applya("http://example.org/bilder/erstes.jpg", kontext)

        assertEquals(listOf("2020/august/erstes.jpg"), writtenRelativeTo(location))
        assertTrue(File(tempDir, "2020/august/erstes.jpg").isFile, "the file is where it should be")
    }

    @Test
    fun `creates the folders of the kontext path that do not exist yet`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, "deeply/nested/path"))

        assertTrue(File(tempDir, "deeply/nested/path/a.jpg").isFile)
    }

    /**
     * The path is a bessource style path, so a caller that built it on windows hands over `\` where
     * this code writes `/`. Without the normalisation that would be one long file name on a posix
     * system instead of a directory.
     */
    @Test
    fun `normalises windows separators in the kontext path`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, "2020\\august"))

        assertTrue(File(tempDir, "2020/august/a.jpg").isFile)
        assertEquals(listOf("2020/august/a.jpg"), writtenRelativeTo(location))
    }

    @Test
    fun `ignores leading and trailing slashes on the kontext path`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, "/2020/august/"))

        assertEquals(listOf("2020/august/a.jpg"), writtenRelativeTo(location))
    }

    @Test
    fun `treats a blank kontext path as the location root`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, "   "))

        assertEquals(listOf("a.jpg"), writtenRelativeTo(location))
    }

    // -------------------------------------------------------------------------------------
    // The file name
    // -------------------------------------------------------------------------------------

    /** The query is not part of the name, or every `?size=large` would become a second file. */
    @Test
    fun `drops the query from the file name`() {
        worker.applya("http://example.org/bilder/a.jpg?size=large", kontextFor(location, ""))

        assertEquals(listOf("a.jpg"), writtenRelativeTo(location))
    }

    @Test
    fun `drops the fragment from the file name`() {
        worker.applya("http://example.org/bilder/a.jpg#anchor", kontextFor(location, ""))

        assertEquals(listOf("a.jpg"), writtenRelativeTo(location))
    }

    /** A gallery names its images `1.jpg`, `2.jpg`, so the name is the last segment and nothing else. */
    @Test
    fun `takes the last path segment as the file name`() {
        worker.applya("http://example.org/a/b/c/deep.jpg", kontextFor(location, ""))

        assertEquals(listOf("deep.jpg"), writtenRelativeTo(location))
    }

    @Test
    fun `decodes percent escapes in the file name`() {
        worker.applya("http://example.org/bilder/das%20Bild.jpg", kontextFor(location, ""))

        assertEquals(listOf("das_Bild.jpg"), writtenRelativeTo(location))
    }

    /**
     * The name comes from a url, i.e. from text the scanned page had a say in. A nested path or a
     * `..` in it must not become a directory or a step out of the folder of the scan.
     */
    @Test
    fun `never lets the url write outside the folder of the scan`() {
        worker.applya("http://example.org/bilder/../../etc/passwd", kontextFor(location, "2020"))

        // only the last segment survives, so the traversal is not merely sanitised away: there is no
        // path left for it to escape with, and the file lands inside the folder of the scan
        assertEquals(listOf("2020/passwd"), writtenRelativeTo(location))
        assertTrue(fetcher.downloads.single().target.path.startsWith(location.uri!!))
    }

    @Test
    fun `names a url that has no last segment after its host`() {
        worker.applya("http://example.org/", kontextFor(location, ""))

        assertEquals(listOf("example.org"), writtenRelativeTo(location))
    }

    /**
     * Two elements of the same name in one folder get a variant each, so neither file replaces the
     * other, when the caller asked for a new download.
     *
     * Without this the second download would overwrite the first, and the medium recorded for the
     * first would be left pointing at bytes that are no longer the ones it was recorded for. The
     * counter goes in front of the extension, so the name keeps saying what it is.
     */
    @Test
    fun `two elements with the same file name get a variant each`() {
        val kontext = kontextFor(location, "2020", alwaysNewDownload = true)

        worker.applya("http://example.org/one/a.jpg", kontext)
        worker.applya("http://example.org/two/a.jpg", kontext)

        assertEquals(listOf("2020/a.1.jpg", "2020/a.jpg"), writtenRelativeTo(location, "2020"))
        // the first element keeps the name, the second gets the variant
        assertEquals(
            mapOf("http://example.org/one/a.jpg" to "a.jpg", "http://example.org/two/a.jpg" to "a.1.jpg"),
            kontext.takenFileNames
        )
        assertEquals(2, fetcher.downloads.size)
    }

    /**
     * Without that, the two share the name and the second is not fetched at all, since by the time it
     * is asked for the first has written the file the name points at.
     */
    @Test
    fun `two elements with the same file name share one file unless a new download is asked for`() {
        val kontext = kontextFor(location, "2020")

        worker.applya("http://example.org/one/a.jpg", kontext)
        worker.applya("http://example.org/two/a.jpg", kontext)

        assertEquals(listOf("2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals(1, fetcher.downloads.size)
        assertEquals("http://example.org/two/a.jpg", kontext.failures.single().element)
    }

    /** A third clash keeps counting, rather than colliding with the variant of the second. */
    @Test
    fun `counts on for every further clash of the same file name`() {
        val kontext = kontextFor(location, "2020", alwaysNewDownload = true)

        worker.applya("http://example.org/one/a.jpg", kontext)
        worker.applya("http://example.org/two/a.jpg", kontext)
        worker.applya("http://example.org/three/a.jpg", kontext)

        assertEquals(listOf("2020/a.1.jpg", "2020/a.2.jpg", "2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals(
            mapOf(
                "http://example.org/one/a.jpg" to "a.jpg",
                "http://example.org/two/a.jpg" to "a.1.jpg",
                "http://example.org/three/a.jpg" to "a.2.jpg"
            ),
            kontext.takenFileNames
        )
    }

    /** A gallery that names its images `1.jpg`, `2.jpg`, ... never clashes, so nothing is suffixed. */
    @Test
    fun `leaves names of a gallery that never clashes alone`() {
        val kontext = kontextFor(location, "2020")

        worker.applya("http://example.org/1.jpg", kontext)
        worker.applya("http://example.org/2.jpg", kontext)
        worker.applya("http://example.org/3.jpg", kontext)

        assertEquals(listOf("2020/1.jpg", "2020/2.jpg", "2020/3.jpg"), writtenRelativeTo(location, "2020"))
        // nothing carries a counter, which would show up as an extra dot in front of the extension
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"), kontext.takenFileNames.values.sorted())
    }

    /**
     * A file already in the folder of the scan is left exactly as it is, and the element is reported
     * rather than fetched again.
     *
     * The name is the one that file already has, so nothing is written beside it and nothing is
     * overwritten: a second import of a gallery adds no files and says which ones it did not fetch.
     * The file here was put there by something other than this scan, which is why it is not in
     * `takenFileNames`: a gallery imported yesterday is on disk and nothing in this kontext knows it.
     */
    @Test
    fun `leaves a file of an earlier scan alone and reports it`() {
        val existing = File(tempDir, "2020/a.jpg").apply {
            parentFile.mkdirs()
            writeText("from an earlier import")
        }
        val kontext = kontextFor(location, "2020")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals("from an earlier import", existing.readText(), "the earlier file was not touched")
        assertTrue(fetcher.downloads.isEmpty(), "nothing was fetched")
        // the plain name, so that the medium of this element points at the file that is there
        assertEquals(mapOf("http://example.org/bilder/a.jpg" to "a.jpg"), kontext.takenFileNames)
        assertEquals(
            "File already exists, so it was not downloaded",
            kontext.failures.single().reason
        )
    }

    /**
     * A new download was asked for, so the earlier file keeps its name and the new one is written
     * beside it.
     *
     * The behaviour this had before there was a choice, kept as the opt in it now is.
     */
    @Test
    fun `writes a new copy beside an earlier file when a new download is asked for`() {
        val existing = File(tempDir, "2020/a.jpg").apply {
            parentFile.mkdirs()
            writeText("from an earlier import")
        }
        val kontext = kontextFor(location, "2020", alwaysNewDownload = true)

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/a.1.jpg", "2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals("from an earlier import", existing.readText(), "the earlier file was overwritten")
        assertEquals(mapOf("http://example.org/bilder/a.jpg" to "a.1.jpg"), kontext.takenFileNames)
        assertEquals(emptyList(), kontext.failures)
    }

    /**
     * A file in another folder of the location is no clash, since it does not occupy the name in the
     * folder this scan writes into.
     *
     * So the file is fetched even though a file of that name exists, which is what makes a per run
     * folder the way to keep two galleries of the same names apart.
     */
    @Test
    fun `ignores files of another folder of the location`() {
        File(tempDir, "2019/a.jpg").apply {
            parentFile.mkdirs()
            writeText("last years gallery")
        }
        val kontext = kontextFor(location, "2020")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        // only what this scan wrote below 2020, so the file of last years gallery does not count
        assertEquals(listOf("2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals("last years gallery", File(tempDir, "2019/a.jpg").readText(), "the old file was not touched")
        assertEquals(emptyList(), kontext.failures, "nothing was reported")
    }

    /**
     * Asking twice for the same element answers the same name, which is what lets [MediaAdder] record
     * a bessource and this worker write the file to the same path.
     */
    @Test
    fun `answers the same name for the same element however often it is asked`() {
        val kontext = kontextFor(location, "2020")

        worker.applya("http://example.org/a.jpg", kontext)
        worker.applya("http://example.org/a.jpg", kontext)

        // the second ask is the same element, so it reuses the name rather than claiming a variant
        assertEquals(listOf("2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals(mapOf("http://example.org/a.jpg" to "a.jpg"), kontext.takenFileNames)
    }

    /** The counter is a property of the scan, so a second scan starts over at the original name. */
    @Test
    fun `starts the counter over for a second scan`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, "2020"))

        val second = kontextFor(location, "2021")
        worker.applya("http://example.org/a.jpg", second)

        // a different folder anyway, and the counter of the first scan did not carry over into it
        assertEquals(mapOf("http://example.org/a.jpg" to "a.jpg"), second.takenFileNames)
        assertEquals(listOf("2020/a.jpg"), writtenRelativeTo(location, "2020"))
        assertEquals(listOf("2021/a.jpg"), writtenRelativeTo(location, "2021"))
    }

    // -------------------------------------------------------------------------------------
    // Which fetcher
    // -------------------------------------------------------------------------------------

    /**
     * The kontext's fetcher rather than a downloader of the worker's own, so a browser scan fetches
     * its files with the same session the page was read with. A page that only offers its urls to a
     * logged in client would otherwise yield media whose files cannot be had at all.
     */
    @Test
    fun `downloads through the fetcher of the kontext`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, ""))

        assertEquals(listOf("http://example.org/a.jpg"), fetcher.downloads.map { it.url })
        assertTrue(fetcher.reads.isEmpty(), "the worker downloads rather than reading the url as text")
    }

    @Test
    fun `downloads without a proxy`() {
        worker.applya("http://example.org/a.jpg", kontextFor(location, ""))

        assertEquals(listOf(false), fetcher.downloads.map { it.withProxy })
    }

    // -------------------------------------------------------------------------------------
    // What it refuses
    // -------------------------------------------------------------------------------------

    /** A http location has no path, so there is nowhere to put the bytes. */
    @Test
    fun `refuses a location that is not a file system location`() {
        val http = givenLocation("http://example.org/main", LocationType.MAIN_HTTP)

        assertThrows<NotAccessibleException> {
            worker.applya("http://example.org/a.jpg", kontextFor(http, ""))
        }
    }

    /**
     * The catchup location of a scan that was not assigned one carries a blank uri, and `File("")`
     * is the working directory of the process. Without this the scan would quietly fill the folder
     * the application was started in.
     */
    @Test
    fun `refuses a location without a path`() {
        val blank = givenLocation("", LocationType.MAIN_FS)

        assertThrows<NotAccessibleException> {
            worker.applya("http://example.org/a.jpg", kontextFor(blank, ""))
        }
        assertTrue(fetcher.downloads.isEmpty(), "nothing was downloaded")
    }

    @Test
    fun `refuses a location whose path is not an existing directory`() {
        val missing = givenLocation(File(tempDir, "no-such-folder").absolutePath, LocationType.MAIN_FS)

        assertThrows<NotAccessibleException> {
            worker.applya("http://example.org/a.jpg", kontextFor(missing, ""))
        }
    }

    /** A scan assigned to the wrong location should fail rather than scatter files elsewhere. */
    @Test
    fun `refuses a location whose path is a file rather than a directory`() {
        val file = File(tempDir, "a-file.txt").apply { writeText("not a folder") }
        val asFile = givenLocation(file.absolutePath, LocationType.MAIN_FS)

        assertThrows<NotAccessibleException> {
            worker.applya("http://example.org/a.jpg", kontextFor(asFile, ""))
        }
    }

    /** A TN location is a folder like any other, so downloading into one is allowed. */
    @Test
    fun `accepts a TN file system location`() {
        val tn = givenLocation(tempDir.absolutePath, LocationType.TN_FS)

        worker.applya("http://example.org/thumb.jpg", kontextFor(tn, "2020"))

        assertEquals(listOf("2020/thumb.jpg"), writtenRelativeTo(tn))
    }

    /** The failure of a download is the caller's to handle, so it is not swallowed. */
    @Test
    fun `lets a failed download through`() {
        fetcher.failing = true

        assertThrows<NotAccessibleException> {
            worker.applya("http://example.org/a.jpg", kontextFor(location, ""))
        }
    }

    // -------------------------------------------------------------------------------------
    // When the scan was told to leave the files where they are
    // -------------------------------------------------------------------------------------

    /**
     * A file that was not fetched is a known gap rather than a silent one.
     *
     * Reported the same way a download that 404s is, since a caller reads both out of one list and
     * cannot act on them differently: the element and the reason it was left alone.
     */
    @Test
    fun `records why an element was not downloaded when the scan excludes downloads`() {
        val kontext = kontextFor(location, "").also { it.skipDownloads = true }

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        val failure = kontext.failures.single()
        assertEquals("http://example.org/bilder/a.jpg", failure.element)
        assertEquals("Download excluded. Need to be done manually", failure.reason)
    }

    @Test
    fun `downloads nothing when the scan excludes downloads`() {
        val kontext = kontextFor(location, "").also { it.skipDownloads = true }

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertTrue(fetcher.downloads.isEmpty(), "nothing was fetched")
        assertEquals(emptyList(), writtenRelativeTo(location), "no file was written")
    }

    /** One entry per element, since a page holds several and each file is a gap of its own. */
    @Test
    fun `records one failure per element of a page`() {
        val kontext = kontextFor(location, "").also { it.skipDownloads = true }

        worker.applya("http://example.org/one.jpg", kontext)
        worker.applya("http://example.org/two.jpg", kontext)

        assertEquals(
            listOf("http://example.org/one.jpg", "http://example.org/two.jpg"),
            kontext.failures.map { it.element }
        )
    }

    /** The exclusion is said rather than thrown, so one excluded file does not end a scan of many. */
    @Test
    fun `does not throw when the scan excludes downloads`() {
        val kontext = kontextFor(location, "").also { it.skipDownloads = true }

        worker.applya("http://example.org/a.jpg", kontext)

        assertEquals(1, kontext.failures.size)
    }

    /**
     * A location that cannot receive files is not refused when nothing is to be written into it.
     *
     * The check would be about where a file goes, and there is no file, so a scan that records only
     * what a page holds is not a scan into a folder that is not there.
     */
    @Test
    fun `does not refuse a location it would not download into`() {
        val http = givenLocation("http://example.org/main", LocationType.MAIN_HTTP)
        val kontext = kontextFor(http, "").also { it.skipDownloads = true }

        worker.applya("http://example.org/a.jpg", kontext)

        assertEquals(1, kontext.failures.size)
        assertTrue(fetcher.downloads.isEmpty())
    }

    /** False by default, so a scan that says nothing keeps downloading what it found. */
    @Test
    fun `downloads as usual when the scan says nothing about it`() {
        val kontext = kontextFor(location, "")

        worker.applya("http://example.org/a.jpg", kontext)

        assertEquals(listOf("http://example.org/a.jpg"), fetcher.downloads.map { it.url })
        assertEquals(emptyList(), kontext.failures)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun kontextFor(
        location: Location,
        locationPath: String,
        alwaysNewDownload: Boolean = false
    ) = ScanningKontext(
        location,
        Mset(),
        arrayListOf(),
        fetcher,
        locationPath,
        alwaysNewDownload = alwaysNewDownload
    )

    private fun givenLocation(uri: String, locationType: LocationType) = Location().apply {
        this.uri = uri
        name = uri
        this.locationType = locationType.i
        inuse = 1
        this.storage = Storage()
    }

    /**
     * The paths of the files below [folder] of [location], `/` separated and relative to [location].
     *
     * Read back off the file system rather than off the recorded downloads, so a file that was written
     * to an unexpected place still shows up here. Scoped to [folder] so that a file an earlier scan
     * left in the location does not turn up in the listing of this one.
     *
     * Sorted, which puts `a.1.jpg` before `a.jpg`. Which element got which name is therefore asserted
     * through [ScanningKontext.takenFileNames] rather than through the order of this list.
     */
    private fun writtenRelativeTo(location: Location, folder: String = ""): List<String> {
        val root = File(location.uri!!)
        val from = if (folder.isEmpty()) root else File(root, folder)

        return from.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()
    }

    /** One recorded download, since the arguments of `downloadTo` are three rather than one. */
    private data class Download(val url: String, val target: File, val withProxy: Boolean)

    /**
     * A [Fetcher] that writes a marker file instead of making a request, and records what it was
     * asked for.
     *
     * Writing a file rather than only recording it is what makes the path assertions meaningful: a
     * worker that computed the right target but handed it to nothing would otherwise pass.
     */
    private class RecordingFetcher : Fetcher {

        val downloads = mutableListOf<Download>()
        val reads = mutableListOf<String>()

        /** makes every download fail, to check that a failure is not swallowed */
        var failing = false

        override fun getAsString(urlString: String, withProxy: Boolean): String {
            reads.add(urlString)
            return ""
        }

        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {
            downloadTo(urlString, File(directory, "downloaded"))
        }

        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            if (failing) throw NotAccessibleException("download of $urlString refused")
            downloads.add(Download(urlString, target, withProxy))
            target.parentFile?.mkdirs()
            target.writeText("content of $urlString")
            return target
        }
    }
}
