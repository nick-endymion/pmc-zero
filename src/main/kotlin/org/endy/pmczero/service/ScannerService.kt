package org.endy.pmczero.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.ScannerShort
import org.endy.pmczero.model.modern.SerializedScanner
import org.endy.pmczero.model.scraper.*
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.repository.SerializedScannerRepository
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.SourceToScanTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ScannerService(
    private val serializedScannerRepository: SerializedScannerRepository,
    private val scraper: Scraper,
    private val locationService: LocationService
) {

    lateinit var scannerShorts: List<ScannerShort>
    lateinit var format: Json
//        {"htmlParser": {"type": "Domparser","regex": "(.*)","tag": "","attribute":""},"worker":{"type":"setCreator"}}

    init {
        val module = SerializersModule {
            polymorphic(Parser::class) {
                subclass(DomParser::class)
            }
            polymorphic(Parser::class) {
                subclass(PassThroughParser::class)
            }
            polymorphic(Parser::class) {
                subclass(RegexParser::class)
            }
            polymorphic(Worker::class) {
                subclass(SetCreator::class)
            }
            polymorphic(Worker::class) {
                subclass(StructuredWorker::class)
            }
            polymorphic(Worker::class) {
                subclass(MediaAdder::class)
            }
        }
        format = Json {
            serializersModule = module
            prettyPrint = true
        }
        scannerShorts = serializedScannerRepository.findAllByValid(true)
    }

    fun findById(id: Int): SerializedScanner {
        return serializedScannerRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun findByUrl(url: String): List<ScannerShort> {
        scannerShorts = serializedScannerRepository.findAllByValid(true) //todo
        return scannerShorts.filter { it.getRegex().toRegex().matches(url) }
    }


//    fun search(searchTerm: String): List<Mset> {
//        return msetRepository.findAllByNameContaining(searchTerm)
//    }

    fun save(serializedScanner: SerializedScanner): SerializedScanner {
        serializedScanner.valid = false
        val s1 = serializedScannerRepository.save(serializedScanner)
        val s2 = deserializeAndSerialize(s1)
        serializedScannerRepository.save(s2)
        return s2
    }

    fun delete(id: Int) {
        serializedScannerRepository.delete(findById(id))
    }
//----------------

    fun deserializeAndSerialize(serializedScanner: SerializedScanner): SerializedScanner {
        println(serializedScanner.serialization)
        val scanner = deserialize(serializedScanner.serialization!!)
        serializedScanner.serialization = serialize(scanner)
        serializedScanner.valid = true
        println(serializedScanner.serialization)
        return serializedScanner
    }

    fun getScanner(id: Int): Scanner {
        val serializedScanner = findById(id)
        return deserialize(serializedScanner.serialization!!)
    }

    fun deserialize(serialization: String): Scanner {
        return format.decodeFromString<Scanner>(serialization)
    }

    fun serialize(scanner: Scanner): String {
        return format.encodeToString(scanner)
    }

    fun scan(scannerId: Int, url: String): Mset {
        val serializedScanner = findById(scannerId)
        val scanner = deserialize(serializedScanner.serialization!!)
        val sc = scraper.scan(scanner, url)
        return sc.mset ?: throw Exception()
    }


    fun scan(sts: SourceToScanTO): Mset {
        val serializedScanner = findById(sts.scannerId)
        val scanner = deserialize(serializedScanner.serialization!!)
        val url = sts.url ?: locationService.findById(sts.bookmarkId!!).uri
        val sc = scraper.scan(scanner, url!!, sts.locationId)
        if (sts.locationId != null)
            scraper.changeToRealLocation(sc, locationService.findById(sts.locationId!!))
        return sc.mset ?: throw Exception()
    }

//
//    val urls = mset.media.flatMap { it.bessources.map { it.name ?: "" } }
//    val (commonUrlStart, locations) = locationService.getLocationStartingWith(urls)
//    return ScanningResultTO(mset.toTOwithMedia(true), commonUrlStart, locations.map { it.toTO() })


}
