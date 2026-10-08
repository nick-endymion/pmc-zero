package org.endy.pmczero.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.ScannerShort
import org.endy.pmczero.model.modern.Scanner
import org.endy.pmczero.model.scraper.*
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.repository.SerializedScannerRepository
import org.endy.pmczero.to.SourceToScanTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ScannerService(
    private val serializedScannerRepository: SerializedScannerRepository,
    private val scraperService: ScraperService,
    private val locationService: LocationService,
    private val bookmarkService: BookmarkService
) {

    lateinit var scannerShorts: List<ScannerShort>
    lateinit var format: Json
//        {"htmlParser": {"type": "Domparser","regex": "(.*)","tag": "","attribute":""},"worker":{"type":"setCreator"}}

    init {
        // the one format of the project, see [ScanFormat]. Shared rather than built here, so a scraper
        // written by this service and one read by an endpoint that was handed a serialized scraper are
        // the same json and not merely two that happen to agree today.
        format = ScanFormat.json
        scannerShorts = serializedScannerRepository.findAllByValid(true)
    }

    fun findById(id: Int): Scanner {
        return serializedScannerRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun findByUrl(url: String?): List<ScannerShort> {
        scannerShorts = serializedScannerRepository.findAllByValid(true)
        if (url == null) return scannerShorts
        return scannerShorts.filter { it.getRegex().toRegex().matches(url) }
    }


//    fun search(searchTerm: String): List<Mset> {
//        return msetRepository.findAllByNameContaining(searchTerm)
//    }

    fun save(scanner: Scanner): Scanner {
        scanner.valid = false
        val s1 = serializedScannerRepository.save(scanner)
        val s2 = deserializeAndSerialize(s1)
        serializedScannerRepository.save(s2)
        return s2
    }

    fun delete(id: Int) {
        serializedScannerRepository.delete(findById(id))
    }
//----------------

    /**
     * The scanner stored as [id], stored again as a new scanner, answered as the new one.
     *
     * For a scraper that is worth tweaking rather than rebuilding: the copy is the one to change a
     * regex or a selector on, so the scanner it came from stays as it was and keeps answering for the
     * pages it was made for. Every field is carried over, the scraper json included, since a scanner is
     * a name, a url regex, a [org.endy.pmczero.model.modern.Scanner.supplierIdentifcator], an example
     * and a serialized scraper, with no row of its own to reconcile.
     *
     * The copy goes through [save] rather than straight to the repository, so its serialization is
     * deserialized and written back as [save] writes every scanner's: a copy of a scraper that this
     * application cannot read is refused here rather than stored and failing on its first run.
     *
     * The new scanner has no id until the database gives it one, which is what makes this a copy rather
     * than an edit of the original. Nothing of the original is touched.
     *
     * @param name the name of the copy, "<name> (copy)" when blank. A name is free text, so a copy may
     * well end up sharing one with another scanner, which is what [ScannerInitializer] looks at to
     * decide whether a default is already there
     * @throws org.endy.pmczero.exception.NotFoundException when no scanner has that id
     */
    @JvmOverloads
    fun copy(id: Int, name: String? = null): Scanner {
        val original = findById(id)

        val copy = Scanner().also {
            it.name = name?.takeIf { given -> given.isNotBlank() } ?: "${original.name} (copy)"
            it.regex = original.regex
            it.supplierIdentifcator = original.supplierIdentifcator
            it.example = original.example
            it.serialization = original.serialization
        }

        return save(copy)
    }

//----------------

    fun deserializeAndSerialize(scanner: Scanner): Scanner {
        println(scanner.serialization)
        val scraper = deserialize(scanner.serialization!!)
        scanner.serialization = serialize(scraper)
        scanner.valid = true
        println(scanner.serialization)
        return scanner
    }

    fun getScanner(id: Int): Scraper {
        val serializedScanner = findById(id)
        return deserialize(serializedScanner.serialization!!)
    }

    fun deserialize(serialization: String): Scraper {
        return format.decodeFromString<Scraper>(serialization)
    }

    fun serialize(scraper: Scraper): String {
        return format.encodeToString(scraper)
    }

    fun scan(scannerId: Int, url: String): Mset {
        val serializedScanner = findById(scannerId)
        val scanner = deserialize(serializedScanner.serialization!!)
        val sc = scraperService.scan(scanner, url)
        return sc.mset ?: throw Exception()
    }


    fun scan(sts: SourceToScanTO): Mset {
        val serializedScanner = findById(sts.scannerId)
        val scanner = deserialize(serializedScanner.serialization!!)
        val url = sts.url ?: bookmarkService.findById(sts.bookmarkId!!).url
        val sc = scraperService.scan(scanner, url!!, sts.locationId)
//        if (sts.locationId != null)
//            scraperService.changeToRealLocation(sc, locationService.findById(sts.locationId!!))
        return sc.mset ?: throw Exception()
    }

//
//    val urls = mset.media.flatMap { it.bessources.map { it.name ?: "" } }
//    val (commonUrlStart, locations) = locationService.getLocationStartingWith(urls)
//    return ScanningResultTO(mset.toTOwithMedia(true), commonUrlStart, locations.map { it.toTO() })


}
