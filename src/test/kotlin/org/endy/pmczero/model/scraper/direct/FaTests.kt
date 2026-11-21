package org.endy.pmczero.model.scraper.direct

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.FailedDownloads
import org.endy.pmczero.service.Fetcher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaTests {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `is a direct scraper`() {
        val scraper: DirectScraper = Fa(FakeFetcher("<html></html>"))

        assertTrue(scraper is Fa)
    }

    @Test
    fun `numbers the urls from zero through the top number`() {
        val urls = Fa.numberedUrlsOf("https://www.test.de/view/1234567/#3")

        assertEquals(
            listOf(
                "https://www.test.de/view/1234567/#0",
                "https://www.test.de/view/1234567/#1",
                "https://www.test.de/view/1234567/#2",
                "https://www.test.de/view/1234567/#3"
            ),
            urls
        )
    }

    @Test
    fun `keeps the url before the fragment when it numbers the urls`() {
        val urls = Fa.numberedUrlsOf("https://www.test.de/view/1234567/?full=1#2")

        assertEquals(
            listOf(
                "https://www.test.de/view/1234567/?full=1#0",
                "https://www.test.de/view/1234567/?full=1#1",
                "https://www.test.de/view/1234567/?full=1#2"
            ),
            urls
        )
    }

    @Test
    fun `keeps a url without a numeric fragment as it is`() {
        val url = "https://www.test.de/view/1234567/"

        assertEquals(listOf(url), Fa.numberedUrlsOf(url))
    }

    @Test
    fun `keeps a url whose fragment has no number as it is`() {
        val url = "https://www.test.de/view/1234567/#latest"

        assertEquals(listOf(url), Fa.numberedUrlsOf(url))
    }

    @Test
    fun `loads every numbered url`() {
        val fetcher = FakeFetcher("<html></html>")

        Fa(fetcher).load("https://www.test.de/view/1234567/#2", scanningKontext(fetcher))

        assertEquals(
            listOf(
                "https://www.test.de/view/1234567/#2",
                "https://www.test.de/view/1234567/#0",
                "https://www.test.de/view/1234567/#1",
                "https://www.test.de/view/1234567/#2"
            ),
            fetcher.requestedUrls
        )
    }

    @Test
    fun `creates a set named from the page title`() {
        val fetcher = FakeFetcher(
            """
                <html>
                    <head><title>The gallery title</title></head>
                    <body></body>
                </html>
            """.trimIndent()
        )
        val kontext = scanningKontext(fetcher, null)

        Fa(fetcher).load("https://www.test.de/view/1234567/", kontext)

        assertEquals("The gallery title", kontext.mset!!.name)
    }

    @Test
    fun `adds the page description to the set`() {
        val fetcher = FakeFetcher(
            """
                <html><body>
                    <div id="cnt_description">
                        <span>A description of the gallery</span>
                    </div>
                </body></html>
            """.trimIndent()
        )
        val kontext = scanningKontext(fetcher)

        Fa(fetcher).load("https://www.test.de/view/1234567/", kontext)

        assertEquals("A description of the gallery", kontext.mset!!.description)
    }

    @Test
    fun `finds the full image urls on every numbered page`() {
        val page0 = "https://www.test.de/view/1234567/#0"
        val page1 = "https://www.test.de/view/1234567/#1"
        val page2 = "https://www.test.de/view/1234567/#2"
        val fetcher = FakeFetcher(
            "",
            mapOf(
                page0 to """
                    <html><body>
                        <img src="/images/full-0.jpg">
                        <img src="/images/thumbnail.jpg">
                    </body></html>
                """.trimIndent(),
                page1 to """
                    <html><body>
                        <img src="//cdn.test.de/images/full-1.jpg">
                    </body></html>
                """.trimIndent(),
                page2 to """
                    <html><body>
                        <img src="/images/full-0.jpg">
                    </body></html>
                """.trimIndent()
            )
        )

        val kontext = scanningKontext(fetcher)
        val imageUrls = Fa(fetcher).fullImageUrlsOf(
            listOf(page0, page1, page2),
            kontext
        )

        assertEquals(
            listOf(
                "https://www.test.de/images/full-0.jpg",
                "https://cdn.test.de/images/full-1.jpg"
            ),
            imageUrls
        )
        assertEquals(listOf(page0, page1, page2), fetcher.requestedUrls)
        assertEquals(imageUrls, fetcher.downloadedUrls)
        assertEquals(listOf("full-0.jpg", "full-1.jpg"), kontext.mset!!.media.map { it.name })
        assertTrue(File(tempDir, "full-0.jpg").isFile)
        assertTrue(File(tempDir, "full-1.jpg").isFile)
    }

    @Test
    fun `records a failed image and downloads the remaining images`() {
        val page = "https://www.test.de/view/1234567/#0"
        val full0 = "https://www.test.de/images/full-0.jpg"
        val full1 = "https://www.test.de/images/full-1.jpg"
        val fetcher = FakeFetcher(
            "",
            mapOf(
                page to """
                    <html><body>
                        <img src="/images/full-0.jpg">
                        <img src="/images/full-1.jpg">
                    </body></html>
                """.trimIndent()
            ),
            setOf(full0)
        )
        val kontext = scanningKontext(fetcher)

        FailedDownloads.clear()
        try {
            val imageUrls = Fa(fetcher).fullImageUrlsOf(listOf(page), kontext)

            assertEquals(listOf(full0, full1), imageUrls)
            assertEquals(listOf(full0, full1), fetcher.downloadedUrls)
            assertEquals(listOf("full-0.jpg", "full-1.jpg"), kontext.mset!!.media.map { it.name })
            assertEquals(listOf(full0), kontext.failures.map { it.element })
            assertTrue(File(tempDir, "full-1.jpg").isFile)
        } finally {
            FailedDownloads.clear()
        }
    }

    @Test
    fun `reads the last number in the url fragment`() {
        assertEquals(123, Fa.topNumberOf("https://example.org/gallery?x=1#123"))
    }

    @Test
    fun `uses the last number when the fragment holds more than one`() {
        assertEquals(456, Fa.topNumberOf("https://example.org/gallery#123/456"))
    }

    @Test
    fun `has no top number when the url has no fragment`() {
        assertNull(Fa.topNumberOf("https://example.org/gallery?x=1"))
    }

    private fun scanningKontext(fetcher: Fetcher, mset: Mset? = Mset()) = ScanningKontext(
        location = Location().apply {
            uri = tempDir.absolutePath
            locationType = LocationType.MAIN_FS.idx
            storage = Storage()
        },
        mset = mset,
        attributes = arrayListOf(),
        fetcher = fetcher,
        locationPath = ""
    )

    private class FakeFetcher(
        private val content: String,
        private val contentsByUrl: Map<String, String> = emptyMap(),
        private val failingUrls: Set<String> = emptySet()
    ) : Fetcher {
        val requestedUrls = mutableListOf<String>()
        val downloadedUrls = mutableListOf<String>()

        override fun getAsString(
            urlString: String,
            withProxy: Boolean,
            waitForLoadEvent: Boolean
        ): String {
            requestedUrls.add(urlString)
            return contentsByUrl[urlString] ?: content
        }

        override fun downLoadToFile(urlString: String, directory: String, withProxy: Boolean) = Unit

        override fun downloadTo(urlString: String, target: File, withProxy: Boolean): File {
            downloadedUrls.add(urlString)
            if (urlString in failingUrls) {
                throw NotAccessibleException("download of $urlString refused")
            }
            target.parentFile?.mkdirs()
            target.writeText("content of $urlString")
            return target
        }
    }
}
