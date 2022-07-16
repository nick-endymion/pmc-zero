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

@Service
class SerializedScannerService(
    private val mediaRepository: MediaRepository,
    private val msetRepository: MsetRepository,
    private val mediaService: MediaService
) {

    fun deserialize(serializedScanner: SerializedScanner) : SerializedScanner{
        println(serializedScanner.serialization)

//        val obj =
//            Scanner(DomParser("(.*)", "", ""), SetCreator())

        val module = SerializersModule {
            polymorphic(HtmlParser::class) {
                subclass(DomParser::class)
            }
            polymorphic(Worker::class) {
                subclass(SetCreator::class)
            }
        }
        val format = Json {
            serializersModule = module
            prettyPrint = true
        }


//        val x = "{\"htmlParser\": {\"type\": \"Domparser\",\"regex\": \"(.*)\",\"tag\": \"\",\"attribute\":\"\"},\"worker\":{\"type\":\"setCreator\"}}\n"
////        val x =
////            "\"{\\\"htmlParser\\\":{\\\"type\\\":\\\"Domparser\\\",\\\"regex\\\":\\\"(.*)\\\",\\\"tag\\\":\\\"\\\",\\\"attribute\\\":\\\"\\\"},\\\"worker\\\":{\\\"type\\\":\\\"setCreator\\\"}}\""
//        println(x)
//        val scannerDesialized = format.decodeFromString<Scanner>(x)
        val scannerDesialized = format.decodeFromString<Scanner>(serializedScanner.serialization!!)

        serializedScanner.serialization = format.encodeToString(scannerDesialized)
        println(serializedScanner.serialization)
        return serializedScanner

    }

}
