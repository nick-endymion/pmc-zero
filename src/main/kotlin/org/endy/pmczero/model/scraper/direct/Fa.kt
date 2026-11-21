package org.endy.pmczero.model.scraper.direct

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.SetDescriptionAdder
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.Fetcher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * What [Fa.load] found on a FurAffinity page.
 *
 * @param content the downloaded page
 * @param topNumber the last number in the fragment of the requested url, or null when it has none
 * @param submissionImageUrls the urls of the submission images found on the page
 */
data class FaPage(
    val content: String,
    val topNumber: Int?,
    val submissionImageUrls: List<String>
)

@Component
class Fa(
    @Qualifier("downloader")
    private val fetcher: Fetcher = Downloader.Default
) : DirectScraper {

    override val name: String = "Fa"

    /**
     * Loads the FurAffinity page at [url], creates or names its set from the page title, and downloads
     * the full-size images found on it.
     *
     * @param scanningKontext the scan the images are downloaded into
     */
    override fun load(url: String, scanningKontext: ScanningKontext) {
        val content =  fetcher.getAsString(url)

        val titleParser = DomParser("(.*)", "title", "")
        val titles = titleParser.getElements(listOf(content), url)
        val setCreator = SetCreator()
        for (title in titles) {
            setCreator.applya(title, scanningKontext)
        }

        val tagParser = DomParser("(.+)", "#cnt_cats a", "")
        val tags = tagParser.getElements(listOf(content), url)
                scanningKontext.attributes.addAll(tags)

        val descriptionParser = DomParser("(.+)", "#cnt_description span", "")
        val descriptions = descriptionParser.getElements(listOf(content), url)
        val descriptionAdder = SetDescriptionAdder()
        for (description in descriptions) {
            descriptionAdder.applya(description, scanningKontext)
        }

        val topNumber = topNumberOf(url)
        val listOfUrls = numberedUrlsOf(url, topNumber)

        fullImageUrlsOf(listOfUrls, scanningKontext)
    }

    /**
     * The full-size image urls found on [urls], added as media of [ScanningKontext.mset] and
     * downloaded into the location of [scanningKontext].
     *
     * Every url is fetched and its image sources are read as absolute urls, so relative and
     * protocol-relative sources can be downloaded as they are. A full-size image is recognized by
     * `full` in its url; duplicates across the numbered pages are returned and downloaded only once.
     * A download that fails is recorded in [ScanningKontext.failures], and the remaining images are
     * still attempted.
     */
    fun fullImageUrlsOf(urls: List<String>, scanningKontext: ScanningKontext): List<String> {
        val parser = DomParser("(.*full.*)", "img[src]", "abs:src")
        val mediaAdder = MediaAdder()
        val downloader = RecoveryWorker(FileDownloader())

        val imageUrls = urls.flatMap { pageUrl ->
            val content = fetcher.getAsString(pageUrl)
            val foundImages = parser.getElements(listOf(content), pageUrl)

            for (imageUrl in foundImages) {
                mediaAdder.applya(imageUrl, scanningKontext)
                downloader.applya(imageUrl, scanningKontext)
            }

            foundImages
        }.distinct()

        return imageUrls
    }

    companion object {
        private val NUMBER = Regex("\\d+")

        /**
         * The numbered urls described by [url].
         *
         * A url whose fragment ends in a number is expanded into the urls numbered from zero through
         * that number, with the original fragment replaced. A url without such a number is answered as
         * it is, since there is no range to expand it into.
         *
         * For example, `https://www.test.de/view/1234567/#3` answers:
         *
         * - `https://www.test.de/view/1234567/#0`
         * - `https://www.test.de/view/1234567/#1`
         * - `https://www.test.de/view/1234567/#2`
         * - `https://www.test.de/view/1234567/#3`
         */
        fun numberedUrlsOf(url: String): List<String> = numberedUrlsOf(url, topNumberOf(url))

        private fun numberedUrlsOf(url: String, topNumber: Int?): List<String> {
            if (topNumber == null) return listOf(url)

            val urlWithoutFragment = url.substringBefore('#')
            return (0..topNumber).map { "$urlWithoutFragment#$it" }
        }

        /**
         * The last number in the fragment of [url], or null when the url has no fragment with a number.
         *
         * Only the fragment is read, so a number in the query does not count. A fragment may hold more
         * than one number (`#123/456`), in which case the last one is the top number.
         */
        fun topNumberOf(url: String): Int? {
            val fragment = url.substringAfter('#', missingDelimiterValue = "")
            if (fragment.isEmpty()) return null

            return NUMBER.findAll(fragment)
                .lastOrNull()
                ?.value
                ?.toIntOrNull()
        }
    }
}
