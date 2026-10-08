package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Scanner
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.FoundElementsWorker
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.RecoveryWorker
import org.endy.pmczero.model.scraper.SequenceWorker
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.StructuredWorker
import org.endy.pmczero.repository.SerializedScannerRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Creates and persists default [Scanner]s at startup.
 *
 * Each scanner is built as a [Scraper] object, serialized to JSON via [ScanFormat], and stored in the
 * `a.serialized.scanner` table. The `regex` field is used by [ScannerService.findByUrl] to match URLs to
 * scanners, and the `serialization` field holds the JSON representation of the scraper.
 *
 * The scrapers defined here are based on the patterns found in [ScraperRessource] and
 * [ScraperImageImportService]:
 *
 * 1. **Image Scraper** - the standard image scraper: page title as set name, all images recorded as
 *    media and downloaded. This is the most common use case.
 * 2. **Link Collector** - collects all links from a page at level 1, useful for discovering gallery
 *    index pages.
 * 3. **Image Lister** - lists all image URLs from a page without downloading anything. Useful as a
 *    preview before committing to an import.
 * 4. **Title Extractor** - extracts the page title only, useful for quick page identification.
 * 5. **Full Page Scraper** - downloads all images and records all links, combining both collection
 *    and download in one pass.
 * 6. **Gallery Index Lister** - collects the links of an index page at level 1 and the image urls of
 *    the pages behind them at level 2, which is a gallery whose images are not on its index page.
 * 7. **Sequence Image Scraper** - the same scraper as the first one, written with a
 *    [SequenceWorker] rather than with two branches.
 *
 * Only the first, the fifth and the seventh download anything, so only those three can be run through
 * `POST /api/scanners/{id}/scrape`: an import refuses a scraper that records no files, see
 * [ScraperImageImportService.importWithStoredScanner]. The other three are collectors and belong to
 * `POST /api/scanners/{id}/scan`.
 */
@Component
class ScannerInitializer(
    private val serializedScannerRepository: SerializedScannerRepository,
    private val scannerService: ScannerService
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ScannerInitializer::class.java)

    override fun run(args: ApplicationArguments?) {
        logger.info("Initializing default scanners...")

        val scanners = listOf(
            buildImageScraper(),
            buildLinkCollector(),
            buildImageLister(),
            buildTitleExtractor(),
            buildFullPageScraper(),
            buildGalleryIndexLister(),
            buildSequenceImageScraper()
        )

        for (scanner in scanners) {
            try {
                // Check if a scanner with this name already exists
                val existing = serializedScannerRepository.findAllByNameContaining(scanner.name!!)
                    .filter { it.name == scanner.name }

                if (existing.isEmpty()) {
                    val saved = scannerService.save(scanner)
                    logger.info("Created scanner: ${saved.name} (id=${saved.id})")
                } else {
                    logger.info("Scanner already exists: ${scanner.name} (id=${existing.first().id})")
                }
            } catch (e: Exception) {
                logger.error("Failed to create scanner: ${scanner.name}", e)
            }
        }

        logger.info("Scanner initialization complete.")
    }

    /**
     * The standard image scraper: page title as set name, all images recorded as media and downloaded.
     *
     * Based on [ScraperImageImportService.scraperOf] and [ScraperRessource.imageScraper].
     * Uses `abs:src` to resolve relative URLs against the base URI.
     * [RecoveryWorker] wraps the [FileDownloader] so one failed image doesn't abort the entire scan.
     */
    private fun buildImageScraper(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder()),
                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), RecoveryWorker(FileDownloader()))
                )
            )
        )

        return Scanner().apply {
            name = "Image Scraper"
            regex = "(.*)"
            example = "https://example.com/gallery"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * The same scraper as [buildImageScraper], written with a [SequenceWorker].
     *
     * Identical in what it does, and stored as a second scanner rather than in place of the first so
     * that both spellings of the same thing can be looked at in a list and run against a page to see
     * that they agree. The one that is not the [org.endy.pmczero.service.ScraperImageImportService.scraperOf]
     * of the code is kept as [Image Scraper], since that is the one being read by every other part of
     * the application.
     *
     * What changes is the shape, not the outcome. The image scraper is a [StructuredWorker] over three
     * scrapers, of which two carry the same [DomParser] because both [MediaAdder] and [FileDownloader]
     * work on the same element and a [StructuredWorker] hands each of its scrapers a set of its own.
     * This puts those two in a [SequenceWorker] instead, so there is one scraper over the images and
     * the steps over one element are listed in it:
     *
     *     Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder())
     *     Scraper(DomParser("(.+)", "img[src]", "abs:src"), RecoveryWorker(FileDownloader()))
     *
     * against
     *
     *     Scraper(
     *         DomParser("(.+)", "img[src]", "abs:src"),
     *         SequenceWorker(listOf(MediaAdder(), RecoveryWorker(FileDownloader())))
     *     )
     *
     * The [RecoveryWorker] stays around the download and not around the sequence, which is the part
     * that has to keep behaving as it did: a file that cannot be fetched is recorded in
     * [org.endy.pmczero.model.ScanningKontext.failures] and the run goes on, rather than ending and
     * taking every image that would have worked with it.
     *
     * Worth having as a scanner of its own because it is the shape to reach for when a scraper grows a
     * step: "record it, then download it, then also list it" is one more entry in a sequence, where it
     * would be one more scraper with a parser that has to be kept in step with the others.
     */
    private fun buildSequenceImageScraper(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(
                        DomParser("(.+)", "img[src]", "abs:src"),
                        SequenceWorker(listOf(MediaAdder(), RecoveryWorker(FileDownloader())))
                    )
                )
            )
        )

        return Scanner().apply {
            name = "Sequence Image Scraper"
            regex = "(.*)"
            example = "https://example.com/gallery"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * Collects all links from a page at level 1.
     *
     * Based on the link collection pattern in [ScraperImageImportService.level2ScraperOf].
     * Useful for discovering gallery index pages or sitemap-style pages.
     */
    private fun buildLinkCollector(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "a[href]", "abs:href"), FoundElementsWorker(1))
                )
            )
        )

        return Scanner().apply {
            name = "Link Collector"
            regex = "(.*)"
            example = "https://example.com/index"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * Lists all image URLs from a page without downloading anything.
     *
     * Based on [ScraperImageImportService.listScraperOf]. Useful as a preview before committing
     * to a full import.
     */
    private fun buildImageLister(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(
                        DomParser("(.+)", "img[src]", "abs:src"),
                        FoundElementsWorker(1)
                    )
                )
            )
        )

        return Scanner().apply {
            name = "Image Lister"
            regex = "(.*)"
            example = "https://example.com/gallery"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * Extracts the page title only.
     *
     * A minimal scraper that just gets the `<title>` element. Useful for quick page identification
     * or as a building block in more complex scraper pipelines.
     */
    private fun buildTitleExtractor(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator())
                )
            )
        )

        return Scanner().apply {
            name = "Title Extractor"
            regex = "(.*)"
            example = "https://example.com/page"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * The links of an index page at level 1, and the image urls of the pages behind them at level 2.
     *
     * The scraper of [ScraperImageImportService.level2ScraperOf] with the defaults it takes, i.e. no
     * [ScraperImageImportService.listLevel2] `linkClass` and no `pattern`: every `a[href]` of the
     * index is followed and every image of the page behind it is collected.
     *
     * The nesting is what makes this a two level answer, and the nesting does the work: the outer
     * [StructuredWorker] has already been handed the index html, while the inner one carries
     * `download = true` and so reads each linked page over the fetcher of the kontext before picking
     * its images. That inner worker is why the two levels are pages rather than elements of one page,
     * and it is also what costs a fetch per linked link.
     *
     * Nothing is downloaded, so this collects rather than imports: run it through
     * `POST /api/scanners/{id}/scan` and look at the two levels before committing to a gallery, which
     * is what `/api/scrape/scraper-image-list-level2` answers for the same page without a stored
     * scanner. A run against a real index page follows its navigation links as well as its gallery
     * ones, since there is no class to tell them apart here, so it is worth watching what it costs
     * before pointing it at a large site.
     */
    private fun buildGalleryIndexLister(): Scanner {
        val linksOfIndex = DomParser("(.*)", "a[href]", "abs:href")

        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    // the links themselves, so the answer shows what was linked as well as what was found
                    Scraper(linksOfIndex, FoundElementsWorker(1)),
                    // and each linked page read for its images: the inner worker downloads, since it is
                    // handed a url rather than the html of a page. Recovery wraps it, so one dead link
                    // ends that branch and not every page with it
                    Scraper(
                        linksOfIndex,
                        RecoveryWorker(
                            StructuredWorker(
                                download = true,
                                scrapers = listOf(
                                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), FoundElementsWorker(2))
                                )
                            )
                        )
                    )
                )
            )
        )

        return Scanner().apply {
            name = "Gallery Index Lister"
            regex = "(.*)"
            example = "https://example.com/index"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }

    /**
     * Full page scraper: downloads all images and records all links.
     *
     * A comprehensive scraper that combines media downloading with link discovery. Images are
     * recorded as media and downloaded, while links are collected at level 1 for further processing.
     * [RecoveryWorker] wraps the [FileDownloader] for resilience.
     */
    private fun buildFullPageScraper(): Scanner {
        val scraper = Scraper(
            PassThroughParser(),
            StructuredWorker(
                download = false,
                scrapers = listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), MediaAdder()),
                    Scraper(DomParser("(.+)", "img[src]", "abs:src"), RecoveryWorker(FileDownloader())),
                    Scraper(DomParser("(.*)", "a[href]", "abs:href"), FoundElementsWorker(1))
                )
            )
        )

        return Scanner().apply {
            name = "Full Page Scraper"
            regex = "(.*)"
            example = "https://example.com/gallery"
            serialization = scannerService.serialize(scraper)
            valid = true
        }
    }
}
