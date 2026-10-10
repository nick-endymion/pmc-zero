package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.FoundElement
import org.endy.pmczero.model.LocationType
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SequenceWorker], which is a list of workers and nothing else.
 *
 * What is worth checking is not that it calls each worker but the two things that make it a worker of
 * several steps rather than one: that a step is handed what the one before it left, since they share
 * one [ScanningKontext], and that a step that throws stops the ones below it, since the worker
 * propagates whatever its steps throw.
 */
class SequenceWorkerTests {

    private lateinit var kontext: ScanningKontext

    @BeforeEach
    fun setUp() {
        kontext = ScanningKontext(writableLocation(), Mset(), arrayListOf(), fetcher, "")
    }

    // -------------------------------------------------------------------------------------
    // What it does with its workers
    // -------------------------------------------------------------------------------------

    @Test
    fun `runs every worker on the element`() {
        SequenceWorker(listOf(SetCreator(), MediaAdder(), FoundElementsWorker(1)))
            .applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals("http://example.org/bilder/a.jpg", kontext.mset?.name)
        assertEquals(1, kontext.mset?.media?.size)
        assertEquals(listOf(FoundElement(1, "http://example.org/bilder/a.jpg")), kontext.foundElements)
    }

    /** Every worker is handed the element itself, not a piece of it, which is the whole difference. */
    @Test
    fun `hands every worker the element as it is`() {
        SequenceWorker(listOf(FoundElementsWorker(1), FoundElementsWorker(2)))
            .applya("  /bilder/a.jpg  ", kontext)

        assertEquals(
            listOf(FoundElement(1, "  /bilder/a.jpg  "), FoundElement(2, "  /bilder/a.jpg  ")),
            kontext.foundElements
        )
    }

    /**
     * They share one kontext, so a step sees what the step before it wrote.
     *
     * A [MediaAdder] before a [FileDownloader] is the case that matters: the medium is recorded, then
     * the file it points at is written, and the path of the two is the same one because both ask the
     * kontext for it.
     */
    @Test
    fun `hands each worker what the one before it left on the kontext`() {
        SequenceWorker(listOf(MediaAdder(), FoundElementsWorker(1)))
            .applya("http://example.org/bilder/a.jpg", kontext)

        val bessource = kontext.mset!!.media.single().bessources.single()
        assertEquals("http://example.org/bilder/a.jpg", kontext.foundElements.single().element)
        assertTrue(bessource.name!!.endsWith("a.jpg"), "the medium names the file the downloader writes")
    }

    /**
     * The workers share one set, so a [SetCreator] in the middle of a sequence names it rather than
     * throwing away what the workers before it recorded.
     *
     * It used to replace the set of the kontext with one of its own; it does not any more, since a run
     * onto a set that is there has to keep that set and the media it holds.
     */
    @Test
    fun `names the set the earlier workers filled rather than replacing it`() {
        SequenceWorker(listOf(MediaAdder(), SetCreator()))
            .applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals("http://example.org/bilder/a.jpg", kontext.mset?.name)
        assertEquals(1, kontext.mset?.media?.size, "the medium of the first worker is still there")
    }

    /** A set that is already named keeps that name, which is a run onto a set that is there. */
    @Test
    fun `leaves the name of a set that has one`() {
        kontext.mset = Mset().also {
            it.id = 55
            it.name = "Aces und mehr"
        }

        SetCreator().applya("der Titel der Seite", kontext)

        assertEquals(55, kontext.mset?.id)
        assertEquals("Aces und mehr", kontext.mset?.name)
    }

    @Test
    fun `runs one worker as readily as several`() {
        SequenceWorker(listOf(FoundElementsWorker(1))).applya("a", kontext)

        assertEquals(listOf(FoundElement(1, "a")), kontext.foundElements)
    }

    /**
     * Nothing to do is a scraper that finds the elements of a page and throws them away.
     *
     * Not refused here: refusing it would mean deciding at runtime that a stored configuration is
     * wrong, and the caller sees the same thing anyway as an import with no media in it.
     */
    @Test
    fun `does nothing for an empty list`() {
        SequenceWorker(emptyList()).applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(emptyList(), kontext.foundElements)
        assertEquals(0, kontext.mset!!.media.size)
        assertEquals(emptyList(), kontext.failures)
    }

    /** One run is one set of results: a second element is handled from the same kontext, not a fresh one. */
    @Test
    fun `handles each element it is handed`() {
        val worker = SequenceWorker(listOf(MediaAdder(), FoundElementsWorker(1)))

        worker.applya("http://example.org/bilder/a.jpg", kontext)
        worker.applya("http://example.org/bilder/b.jpg", kontext)

        assertEquals(2, kontext.mset!!.media.size)
        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/bilder/a.jpg"),
                FoundElement(1, "http://example.org/bilder/b.jpg")
            ),
            kontext.foundElements
        )
    }

    // -------------------------------------------------------------------------------------
    // Failing steps
    // -------------------------------------------------------------------------------------

    /**
     * A worker throws rather than returns, so a step that fails ends the rest of the sequence.
     *
     * [FileDownloader] over a location that cannot receive files is the case: it throws before
     * downloading, and the collector below it never runs, so the failure is not quietly swallowed
     * and neither are the findings that would have followed.
     */
    @Test
    fun `lets a failing worker end the sequence`() {
        kontext.location = notWritableLocation()

        val e = runCatching {
            SequenceWorker(listOf(MediaAdder(), FileDownloader(), FoundElementsWorker(1)))
                .applya("http://example.org/bilder/a.jpg", kontext)
        }.exceptionOrNull()

        assertIs<NotAccessibleException>(e)
        assertEquals(emptyList(), kontext.foundElements, "the worker below the failure did not run")
        assertEquals(1, kontext.mset!!.media.size, "the one above it did")
    }

    /** Wrapping the whole sequence keeps the run going: the steps after a failure are skipped for that element. */
    @Test
    fun `keeps the steps before a failure when the sequence is wrapped in a recovery worker`() {
        kontext.location = notWritableLocation()

        val worker = RecoveryWorker(
            SequenceWorker(listOf(MediaAdder(), FileDownloader(), FoundElementsWorker(1)))
        )

        worker.applya("http://example.org/bilder/a.jpg", kontext)
        worker.applya("http://example.org/bilder/b.jpg", kontext)

        // both media were recorded, both downloads failed, and the collector ran for neither
        assertEquals(2, kontext.mset!!.media.size)
        assertEquals(emptyList(), kontext.foundElements)
        assertEquals(
            listOf("http://example.org/bilder/a.jpg", "http://example.org/bilder/b.jpg"),
            kontext.failures.map { it.element }
        )
    }

    /**
     * Wrapping one step is the difference between losing a download and losing everything after it.
     *
     * [MediaAdder] before the download and the collector after it, so the collector runs on both
     * elements here while the file of neither was written.
     */
    @Test
    fun `skips only the failing step when the recovery worker wraps that one`() {
        kontext.location = notWritableLocation()

        val worker = SequenceWorker(
            listOf(MediaAdder(), RecoveryWorker(FileDownloader()), FoundElementsWorker(1))
        )

        worker.applya("http://example.org/bilder/a.jpg", kontext)
        worker.applya("http://example.org/bilder/b.jpg", kontext)

        assertEquals(2, kontext.mset!!.media.size)
        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/bilder/a.jpg"),
                FoundElement(1, "http://example.org/bilder/b.jpg")
            ),
            kontext.foundElements,
            "the step below the failure still ran"
        )
        assertEquals(2, kontext.failures.size)
    }

    /** A step that cannot be skipped throws out of the scraper, as any other worker would. */
    @Test
    fun `lets a failure out when no recovery worker is there`() {
        kontext.location = notWritableLocation()

        runCatching {
            SequenceWorker(listOf(FileDownloader()))
                .applya("http://example.org/bilder/a.jpg", kontext)
        }.also { result ->
            assertIs<NotAccessibleException>(result.exceptionOrNull())
            assertEquals(emptyList(), kontext.failures, "nothing was recorded as a failure")
        }
    }

    // -------------------------------------------------------------------------------------
    // In a scraper
    // -------------------------------------------------------------------------------------

    /**
     * The point of the worker: one scraper of a parser and a list of steps, instead of one scraper per
     * step.
     *
     * What the two scrapers of [org.endy.pmczero.service.ScraperImageImportService.scraperOf] do,
     * written once rather than as two branches with the same parser.
     */
    @Test
    fun `a scraper of it records and downloads the images of a page`() {
        val html = """
            <html><body>
            <img src="/bilder/erstes.jpg">
            <img src="https://cdn.de/zweites.png">
            </body></html>
        """.trimIndent()

        Scraper(
            DomParser("(.+)", "img[src]", "abs:src"),
            SequenceWorker(listOf(MediaAdder(), RecoveryWorker(FileDownloader())))
        ).doWork(html, "http://example.org/galerie.html", kontext)

        assertEquals(listOf("erstes.jpg", "zweites.png"), kontext.mset!!.media.map { it.name })
        assertEquals(
            listOf("erstes.jpg", "zweites.png"),
            writtenFiles().map { it.name },
            "every medium points at a file that was written"
        )
    }

    /**
     * Relative urls are resolved once, by the parser, and every step then reads the same url.
     *
     * The collector shows the resolved url, and the medium is named after the file at the end of it,
     * which is what [ScanPath] does on a file system location rather than keeping the path of the page.
     */
    @Test
    fun `hands the resolved url to every step`() {
        Scraper(
            DomParser("(.+)", "img[src]", "abs:src"),
            SequenceWorker(listOf(MediaAdder(), FoundElementsWorker(2)))
        ).doWork("<html><body><img src='relativ.jpg'></body></html>", "http://example.org/unter/", kontext)

        assertEquals(
            listOf(FoundElement(2, "http://example.org/unter/relativ.jpg")),
            kontext.foundElements,
            "the step saw the url a browser would follow"
        )
        assertEquals(
            "relativ.jpg",
            kontext.mset!!.media.single().bessources.single().name,
            "the medium is recorded under the file at the end of it"
        )
    }

    /** It nests, since [Worker] is sealed and a sequence of sequences is still a worker. */
    @Test
    fun `runs a sequence inside a sequence`() {
        SequenceWorker(
            listOf(
                MediaAdder(),
                SequenceWorker(listOf(FoundElementsWorker(1), FoundElementsWorker(2)))
            )
        ).applya("http://example.org/bilder/a.jpg", kontext)

        assertEquals(
            listOf(
                FoundElement(1, "http://example.org/bilder/a.jpg"),
                FoundElement(2, "http://example.org/bilder/a.jpg")
            ),
            kontext.foundElements
        )
    }

    /** Stored as configuration like any other worker, its list and all. */
    @Test
    fun `round trips under its serial name with its workers`() {
        val json = ScanFormat.json.encodeToString(
            Scraper(
                DomParser("(.+)", "img[src]", "abs:src"),
                SequenceWorker(listOf(MediaAdder(), RecoveryWorker(FileDownloader())))
            )
        )

        // on the whitespace of ScanFormat, which is pretty printed: this is a stored scraper, so the
        // json has to read as well as it writes
        assertTrue(json.contains(""""type": "sequence""""), "sequence expected in $json")
        assertTrue(json.contains(""""type": "mediaAdder""""), "mediaAdder expected in $json")
        assertTrue(json.contains(""""type": "recovery""""), "recovery expected in $json")
        assertTrue(json.contains(""""type": "fileDownloader""""), "fileDownloader expected in $json")

        val sequence = assertIs<SequenceWorker>(
            ScanFormat.json.decodeFromString<Scraper>(json).worker
        )

        assertEquals(2, sequence.workers.size)
        assertIs<MediaAdder>(sequence.workers[0])
        val recovery = assertIs<RecoveryWorker>(sequence.workers[1])
        assertIs<FileDownloader>(recovery.worker)
    }

    /** The order is part of what is stored, so a re-save cannot come back with the steps swapped. */
    @Test
    fun `round trips the order of its workers`() {
        val original = Scraper(
            DomParser("(.+)", "img[src]", "abs:src"),
            SequenceWorker(listOf(FoundElementsWorker(1), FoundElementsWorker(2), SetCreator()))
        )

        val json = ScanFormat.json.encodeToString(original)
        val decoded = ScanFormat.json.decodeFromString<Scraper>(json)

        assertEquals(json, ScanFormat.json.encodeToString(decoded))
        val levels = (decoded.worker as SequenceWorker).workers
            .filterIsInstance<FoundElementsWorker>()
            .map { it.level }
        assertEquals(listOf(1, 2), levels)
    }

    /** An empty sequence stored as a scanner row reads back as an empty one rather than as nothing. */
    @Test
    fun `round trips an empty list of workers`() {
        val json = ScanFormat.json.encodeToString(
            Scraper(DomParser("(.+)", "img[src]", "abs:src"), SequenceWorker(emptyList()))
        )

        val sequence = assertIs<SequenceWorker>(
            ScanFormat.json.decodeFromString<Scraper>(json).worker
        )

        assertEquals(emptyList(), sequence.workers)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    private fun writableLocation() = Location().apply {
        id = 1
        name = "bilder"
        uri = tempDir.absolutePath
        locationType = LocationType.MAIN_FS.i
        inuse = 1
        this.storage = Storage()
    }

    /** A location [FileDownloader] refuses, since a http one has no directory to write into. */
    private fun notWritableLocation() = Location().apply {
        id = 2
        name = "http"
        uri = "http://example.org/bilder"
        locationType = LocationType.MAIN_HTTP.i
        inuse = 1
        this.storage = Storage()
    }

    /** The files below [tempDir], so a test can see what was really written. */
    private fun writtenFiles(): List<File> =
        tempDir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()

    @TempDir
    lateinit var tempDir: File

    /** A fetcher that writes what it downloads, so the files a step records really appear. */
    private val fetcher: Fetcher = object : Fetcher {
        override fun getAsString(urlString: String, withProxy: Boolean) = ""
        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) {}
        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            target.parentFile?.mkdirs()
            return target.apply { writeText("content of $urlString") }
        }
    }
}
