package org.endy.pmczero.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.ScannerShort
import org.endy.pmczero.model.modern.SerializedScanner
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.HtmlParser
import org.endy.pmczero.model.scraper.Scanner
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.Worker
import org.endy.pmczero.repository.SerializedScannerRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class SerializedScannerService(
    private val serializedScannerRepository: SerializedScannerRepository
) {

    lateinit var  scannerShorts : List<ScannerShort>
    lateinit var format: Json
//        {"htmlParser": {"type": "Domparser","regex": "(.*)","tag": "","attribute":""},"worker":{"type":"setCreator"}}

    init {
        val module = SerializersModule {
            polymorphic(HtmlParser::class) {
                subclass(DomParser::class)
            }
            polymorphic(Worker::class) {
                subclass(SetCreator::class)
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

//    fun search(searchTerm: String): List<Mset> {
//        return msetRepository.findAllByNameContaining(searchTerm)
//    }

    fun save(serializedScanner: SerializedScanner): SerializedScanner {
        serializedScanner.valid = false
        val s1 = serializedScannerRepository.save(serializedScanner)
        val s2 = deserialize(s1)
        serializedScannerRepository.save(s2)
        return s2
    }

    fun delete(id: Int) {
        serializedScannerRepository.delete(findById(id))
    }
//----------------

    fun deserialize(serializedScanner: SerializedScanner): SerializedScanner {
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

}
