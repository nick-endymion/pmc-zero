package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SerializationTests {

    @MockK
    public lateinit var locationService: LocationService

    @MockK
    public lateinit var downloader: Downloader

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun test() {
        var mediuam = Medium()
        var location = Location().also { it.name = "http://"; it.storage = Storage() }

        every {
            locationService.getLocationStartingWith(any())
        } returns location

        every {
            downloader.getAsString(any())
        } returns "<html><title>TEST</title></html>"


        val scanner = Scanner(
            SimpleParser("(.*fa.*)"),
            SetCreator()
        )
//        val module = SerializersModule {
//            polymorphic(HtmlParser::class) {
//                subclass(SimpleParser::class)
//            }
//            polymorphic(Worker::class) {
//                subclass(SetCreator::class)
//            }
//        }
//
//        val format = Json { serializersModule = module }
        val format = Json

        var scannerSerialized = format.encodeToString(scanner)
        println(format.encodeToString(scannerSerialized))

        val scannerDesialized = format.decodeFromString<Scanner>(scannerSerialized)
        scannerSerialized = format.encodeToString(scannerDesialized)
        println(format.encodeToString(scannerSerialized))

    }
}
