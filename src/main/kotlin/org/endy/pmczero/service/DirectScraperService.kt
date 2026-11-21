package org.endy.pmczero.service

import org.endy.pmczero.exception.BadRequestException
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.scraper.direct.DirectScraper
import org.springframework.stereotype.Service

/**
 * Creates the state and the site-specific scraper needed for a direct scan.
 */
@Service
class DirectScraperService(
    private val locationService: LocationService,
    private val downloader: Downloader,
    directScrapers: List<DirectScraper>
) {

    private val scrapersByName: Map<String, DirectScraper> =
        directScrapers.associateBy { it.name.trim().lowercase() }

    init {
        val duplicateNames = directScrapers
            .groupBy { it.name.trim().lowercase() }
            .filterValues { it.size > 1 }
            .keys
        check(duplicateNames.isEmpty()) {
            "More than one direct scraper has the name(s): ${duplicateNames.joinToString()}"
        }
    }

    /**
     * A fresh scan context for the location with [locationId].
     *
     * The set is left empty for the direct scraper to create and name, so the context does not claim
     * to hold a set before the page has told the scraper what it is about.
     *
     * @param locationId the location the files of the scan are written into
     * @param locationPath the folder below the location the files belong in
     */
    @JvmOverloads
    fun initializeScanningKontext(locationId: Int, locationPath: String = ""): ScanningKontext {
        val location = locationService.findById(locationId)

        return ScanningKontext(
            location = location,
            mset = null,
            attributes = arrayListOf(),
            fetcher = downloader,
            locationPath = locationPath
        )
    }

    /**
     * The direct scraper named [name].
     *
     * Every Spring-managed [DirectScraper] is registered automatically, so a new implementation only
     * has to be a component of that type; this method does not have to be changed. Names are matched
     * without regard to case, so `Fa` and `fa` both select the FurAffinity scraper.
     *
     * @throws BadRequestException when no direct scraper has that name
     */
    fun initializeDirectScraper(name: String): DirectScraper =
        scrapersByName[name.trim().lowercase()]
            ?: throw BadRequestException(
                "There is no direct scraper named '$name'. " +
                    "Expected one of: ${scrapersByName.keys.sorted().joinToString()}"
            )
}
