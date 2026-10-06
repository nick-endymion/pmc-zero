package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Fetcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [MediaAdder], and for the agreement between what it records and what
 * [FileDownloader] writes.
 *
 * The naming itself lives in [ScanPath] and is tested through these two workers, since that is the
 * only way it matters: a name that is wrong in isolation still fails these tests if a file does not
 * end up where the bessource says.
 */
class MediaAdderTests {

    @TempDir
    lateinit var tempDir: File

    private lateinit var worker: MediaAdder
    private lateinit var mset: Mset

    @BeforeEach
    fun setUp() {
        worker = MediaAdder()
        mset = Mset()
    }

    // -------------------------------------------------------------------------------------
    // Against a file system location
    // -------------------------------------------------------------------------------------

    /**
     * The name of a bessource has to be relative to its location, since
     * [org.endy.pmczero.service.LocationService.url] builds the served url as
     * `location.uri + "/" + name`.
     */
    @Test
    fun `names the bessource after the file for a file system location`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("a.jpg"), namesIn(mset))
    }

    /** The path of the scan prefixes the name, so the files of a run sit in the folder they got. */
    @Test
    fun `records the bessource below the path of the scan`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "2020/august")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/august/a.jpg"), namesIn(mset))
    }

    @Test
    fun `normalises windows separators in the path of the scan`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "2020\\august")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/august/a.jpg"), namesIn(mset))
    }

    @Test
    fun `ignores leading and trailing slashes on the path of the scan`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "/2020/august/")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/august/a.jpg"), namesIn(mset))
    }

    /**
     * The whole reason [ScanPath] exists: the file is written to the path the bessource records, so a
     * medium never points at a file that is not there.
     *
     * Compared as the paths of real files rather than as strings, since the bessource name is a path
     * relative to the location and the downloader writes an absolute one.
     */
    @Test
    fun `records the very path the file downloader writes to`() {
        val cases = listOf(
            "http://example.org/bilder/a.jpg" to "",
            "http://example.org/bilder/a.jpg" to "2020/august",
            "http://example.org/deep/nested/b.png" to "2021",
            "http://example.org/bilder/das%20Bild.jpg?size=large" to "2020/august",
            "http://example.org/a.jpg" to "\\windows\\style\\path"
        )

        for ((url, path) in cases) {
            // a mset of its own per case, so a leftover from the previous one cannot be mistaken for
            // the record of this one
            val set = Mset()
            val kontext = ScanningKontext(fsLocation(1, tempDir), set, arrayListOf(), fetcher, path)

            worker.applya(url, kontext)
            FileDownloader().applya(url, kontext)

            val recorded = namesIn(set).single()
            val written = File(tempDir, recorded)

            assertTrue(written.isFile, "$url at '$path' recorded as '$recorded' but no file is there")

            // resolving to the file is not enough on its own: a leading slash or a doubled separator
            // would still leave LocationService.url serving a path that is not there
            assertFalse(recorded.startsWith("/"), "'$recorded' should be relative to the location")
            assertFalse(recorded.contains("//"), "'$recorded' should have single separators")
        }
    }

    // -------------------------------------------------------------------------------------
    // Against a http location
    // -------------------------------------------------------------------------------------

    /**
     * An http location is served out of a url, so the bessource is the part of the element below the
     * uri of the location rather than the file name.
     */
    @Test
    fun `names the bessource after the part of the element below a http location`() {
        val kontext = kontextFor(httpLocation("http://example.org/main"), "")

        worker.applya("http://example.org/main/a.pdf", kontext)

        assertEquals(listOf("a.pdf"), namesIn(mset))
    }

    @Test
    fun `keeps the query of the element on a http location`() {
        val kontext = kontextFor(httpLocation("http://example.org/main"), "")

        worker.applya("http://example.org/main/a.pdf?seite=2", kontext)

        // the url has to keep it, or the bessource points at a different document
        assertEquals(listOf("a.pdf?seite=2"), namesIn(mset))
    }

    @Test
    fun `records a http bessource below the path of the scan as well`() {
        val kontext = kontextFor(httpLocation("http://example.org/main"), "2020/august")

        worker.applya("http://example.org/main/a.pdf", kontext)

        assertEquals(listOf("2020/august/a.pdf"), namesIn(mset))
    }

    /**
     * A page that links a file from another host, i.e. an element the location knows nothing about.
     *
     * [org.endy.pmczero.model.modern.Location.getRightPart] threw a bare [Exception] on this, or read
     * past the end of the element, and one such element took the whole scan down. The url is kept
     * whole instead: whether that file is reachable is the question of whoever asks for its url, and
     * a name that at least points at the real one beats a crash.
     */
    @Test
    fun `keeps the whole url for an element outside the location`() {
        val kontext = kontextFor(httpLocation("http://example.org/main"), "")

        worker.applya("https://cdn.de/andere/datei.pdf", kontext)

        assertEquals(listOf("https://cdn.de/andere/datei.pdf"), namesIn(mset))
    }

    /** An element shorter than the uri of the location used to read past its end. */
    @Test
    fun `keeps the whole url for an element shorter than the location uri`() {
        val kontext = kontextFor(httpLocation("http://example.org/ein/sehr/langer/ort/main"), "")

        worker.applya("http://a.de/x.pdf", kontext)

        assertEquals(listOf("http://a.de/x.pdf"), namesIn(mset))
    }

    // -------------------------------------------------------------------------------------
    // Against the catchup location
    // -------------------------------------------------------------------------------------

    /**
     * A scan with no location has no file for a bessource to point at, only the place the element was
     * found, so the whole url is the name.
     */
    @Test
    fun `records the whole url on a catchup scan`() {
        val kontext = kontextFor(catchupLocation(), "")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("http://example.org/bilder/a.jpg"), namesIn(mset))
    }

    @Test
    fun `records the whole url below the path of a catchup scan`() {
        val kontext = kontextFor(catchupLocation(), "2020/august")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(listOf("2020/august/http://example.org/bilder/a.jpg"), namesIn(mset))
    }

    /**
     * A catchup scan records urls, not files, so a clash on the last segment is two names for one url
     * rather than two files that would overwrite each other. Suffixing here would invent a file that
     * nothing has, so both keep the url.
     */
    @Test
    fun `keeps both urls on a catchup scan when the last segments clash`() {
        val kontext = kontextFor(catchupLocation(), "")

        worker.applya("http://example.org/one/a.jpg", kontext)
        worker.applya("http://example.org/two/a.jpg", kontext)

        assertEquals(
            listOf("http://example.org/one/a.jpg", "http://example.org/two/a.jpg"),
            namesIn(mset)
        )
    }

    // -------------------------------------------------------------------------------------
    // The medium itself
    // -------------------------------------------------------------------------------------

    /** The medium is named after the file alone, so two galleries holding an `a.jpg` read alike. */
    @Test
    fun `names the medium after the file alone`() {
        worker.applya("http://example.org/bilder/a.jpg", kontextFor(fsLocation(1, tempDir), "2020/august"))

        assertEquals("a.jpg", mset.media.single().name)
    }

    @Test
    fun `leaves the query out of the medium name`() {
        worker.applya("http://example.org/bilder/a.jpg?size=large", kontextFor(fsLocation(1, tempDir), ""))

        assertEquals("a.jpg", mset.media.single().name)
    }

    /**
     * Two elements clashing on one file name become two media on two paths, and the second medium is
     * named after its own file.
     *
     * The counter reaches the name of the medium, which is the honest outcome: `a.jpg` and `a.1.jpg`
     * really are two different files, and naming both `a.jpg` in a listing would hide that. Two
     * galleries in *different* folders of a scan still read alike, since the folders are dropped from
     * the name; only a clash inside one folder gets a counter, see
     * [gives two media of different galleries the same name].
     */
    @Test
    fun `gives two media of the same file name the name of their own file`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "2020")

        worker.applya("http://example.org/one/a.jpg", kontext)
        worker.applya("http://example.org/two/a.jpg", kontext)

        assertEquals(listOf("a.jpg", "a.1.jpg"), mset.media.map { it.name })
        assertEquals(listOf("2020/a.jpg", "2020/a.1.jpg"), namesIn(mset))
        assertEquals(
            mapOf("http://example.org/one/a.jpg" to "a.jpg", "http://example.org/two/a.jpg" to "a.1.jpg"),
            kontext.takenFileNames
        )
    }

    /**
     * Two galleries of one scan holding an `a.jpg` each clash, and that is right: an FS location holds
     * files in folders, and both elements are written into the single folder the scan was given, so
     * without a counter one of them would replace the other.
     *
     * A caller who wants them kept apart asks for it, by giving each scan its own
     * [ScanningKontext.locationPath].
     */
    @Test
    fun `two galleries of one scan holding the same file name do clash`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "")

        worker.applya("http://example.org/2020/a.jpg", kontext)
        worker.applya("http://example.org/2021/a.jpg", kontext)

        assertEquals(listOf("a.jpg", "a.1.jpg"), mset.media.map { it.name })
        assertEquals(listOf("a.jpg", "a.1.jpg"), namesIn(mset))
    }

    /** Given a folder each, they land apart and keep the name of their file. */
    @Test
    fun `two galleries in a folder each keep the name of their file`() {
        val first = kontextFor(fsLocation(1, tempDir), "2020")
        worker.applya("http://example.org/2020/a.jpg", first)

        val second = kontextFor(fsLocation(1, tempDir), "2021")
        worker.applya("http://example.org/2021/a.jpg", second)

        assertEquals(listOf("a.jpg", "a.jpg"), mset.media.map { it.name })
        assertEquals(listOf("2020/a.jpg", "2021/a.jpg"), namesIn(mset))
    }

    /**
     * A clash is settled once per scan, so recording and downloading agree.
     *
     * If the second of the two elements got a different name from [MediaAdder] than it did from
     * [FileDownloader], the bessource would point at `a.1.jpg` while the file was written to `a.jpg`,
     * and every medium of the scan would serve a 404 with nothing to say why.
     */
    @Test
    fun `records the same variant the file downloader writes`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "2020")

        for (url in listOf("http://example.org/one/a.jpg", "http://example.org/two/a.jpg")) {
            worker.applya(url, kontext)
            FileDownloader().applya(url, kontext)
        }

        assertEquals(listOf("2020/a.jpg", "2020/a.1.jpg"), namesIn(mset))
        for (medium in mset.media) {
            assertTrue(
                File(tempDir, medium.bessources.single().name!!).isFile,
                "medium ${medium.name} points at a file that is not there"
            )
        }
    }

    @Test
    fun `marks every medium as an internet one`() {
        worker.applya("http://example.org/bilder/a.jpg", kontextFor(fsLocation(1, tempDir), ""))
        worker.applya("http://example.org/bilder/b.mp4", kontextFor(fsLocation(1, tempDir), ""))

        // IMEDIUM says where the medium was found rather than what it is, so the extension of a
        // video among a page of images does not relabel it
        assertEquals(listOf(Mtype.IMEDIUM.i, Mtype.IMEDIUM.i), mset.media.map { it.mtype })
    }

    @Test
    fun `records the primary bessource on the storage of the location`() {
        val location = fsLocation(1, tempDir)

        worker.applya("http://example.org/bilder/a.jpg", kontextFor(location, ""))

        val bessource = mset.media.single().bessources.single()
        assertEquals(RessType.PRIMARY.i, bessource.ressType)
        assertEquals(location.storage, bessource.storage)
    }

    /** The bessource belongs to the medium, so saving the mset writes the whole tree. */
    @Test
    fun `points the bessource back at its medium`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "")

        worker.applya("http://example.org/bilder/a.jpg", kontext)

        val medium = mset.media.single()
        assertEquals(medium, medium.bessources.single().medium)
    }

    @Test
    fun `adds every element to the mset of the scan`() {
        val kontext = kontextFor(fsLocation(1, tempDir), "2020")

        worker.applya("http://example.org/a.jpg", kontext)
        worker.applya("http://example.org/b.jpg", kontext)
        worker.applya("http://example.org/c.jpg", kontext)

        assertEquals(3, mset.media.size)
        assertEquals(listOf("2020/a.jpg", "2020/b.jpg", "2020/c.jpg"), namesIn(mset))
    }

    /** A scan needs an mset to add to, so a worker run without one is a programming error. */
    @Test
    fun `refuses a scan without an mset`() {
        val kontext = ScanningKontext(fsLocation(1, tempDir), null, arrayListOf(), fetcher, "")

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            worker.applya("http://example.org/a.jpg", kontext)
        }
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private val fetcher: Fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean) = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            target.parentFile?.mkdirs()
            target.writeText("content")
            return target
        }
    }

    private fun kontextFor(location: Location, locationPath: String) =
        ScanningKontext(location, mset, arrayListOf(), fetcher, locationPath)

    private fun namesIn(mset: Mset): List<String> =
        mset.media.map { it.bessources.single().name!! }

    private fun fsLocation(id: Int, folder: File) = Location().apply {
        this.id = id
        name = "bilder-$id"
        uri = folder.absolutePath
        locationType = LocationType.MAIN_FS.i
        inuse = 1
        this.storage = Storage()
    }

    private fun httpLocation(uri: String) = Location().apply {
        id = 1
        name = uri
        this.uri = uri
        locationType = LocationType.MAIN_HTTP.i
        inuse = 1
        this.storage = Storage()
    }

    /** the placeholder of a scan that was not assigned a location: no type and an empty uri */
    private fun catchupLocation() = Location().apply {
        name = "Catchup Location"
        uri = ""
        this.storage = Storage()
    }
}
