package org.endy.pmczero.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.MsetRepository
import org.springframework.stereotype.Service
import org.endy.pmczero.model.modern.SerializedScanner
import org.endy.pmczero.model.scraper.DomParser
import org.endy.pmczero.model.scraper.HtmlParser
import org.endy.pmczero.model.scraper.Scanner
import org.endy.pmczero.model.scraper.SetCreator
import org.endy.pmczero.model.scraper.Worker
import org.endy.pmczero.repository.SerializedScannerRepository
import org.springframework.data.repository.findByIdOrNull

@Service
class SerializedScannerService(
    private val serializedScannerRepository: SerializedScannerRepository
) {

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
    }

    fun deserialize(serializedScanner: SerializedScanner): SerializedScanner {
        println(serializedScanner.serialization)
        val scanner = deserialize(serializedScanner.serialization!!)
        serializedScanner.serialization = serialize(scanner)
        println(serializedScanner.serialization)
        return serializedScanner
    }

    fun getScanner(id: Int): Scanner {
        val serializedScanner = serializedScannerRepository.findByIdOrNull(id) ?: throw Exception()
        return deserialize(serializedScanner.serialization!!)
    }

    fun deserialize(serialization: String): Scanner {
        return format.decodeFromString<Scanner>(serialization)
    }

    fun serialize(scanner: Scanner): String {
        return format.encodeToString(scanner)
    }

}
