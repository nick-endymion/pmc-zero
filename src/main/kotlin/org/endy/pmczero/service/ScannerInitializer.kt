package org.endy.pmczero.service

import org.endy.pmczero.model.modern.Scanner
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.FileDownloader
import org.endy.pmczero.model.scraper.FoundElementsWorker
import org.endy.pmczero.model.scraper.MediaAdder
import org.endy.pmczero.model.scraper.PassThroughParser
import org.endy.pmczero.model.scraper.RecoveryWorker
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
            buildFullPageScraper()
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
