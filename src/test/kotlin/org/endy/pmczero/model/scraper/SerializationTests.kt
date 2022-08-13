package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import org.junit.jupiter.api.Test

class SerializationTests {

    @Test
    fun `serialization and deserializtion work`() {

        val scanner = Scraper(
//           SimpleParser("(.*fa.*)", "a"),
            DomParser("(.*)", "ad", "adfa"),
            SetCreator()
        )

        var scannerSerialized = Json.encodeToString<Scraper>(scanner)
        println(Json.encodeToString(scannerSerialized))

        val scannerDesialized = Json.decodeFromString<Scraper>(scannerSerialized)
        scannerSerialized = Json.encodeToString(scannerDesialized)
        println(Json.encodeToString(scannerSerialized))
    }

    @Test
    fun `serialization and deserializtion work 2`() {

        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "", ""), SetCreator()),
                    Scraper(
                        DomParser("(.*)", "", ""), MediaAdder()
                    )
                )
            )
        )

        var scannerSerialized = Json.encodeToString(scanner)
        println(Json.encodeToString(scannerSerialized))

        val scannerDesialized = Json.decodeFromString<Scraper>(scannerSerialized)
        scannerSerialized = Json.encodeToString(scannerDesialized)
        println(Json.encodeToString(scannerSerialized))
    }

    @Test
    fun `serialization and deserializtion work 3`() {

        val scanner =
            StructuredWorker(
                true,
                listOf(
                    Scraper(
                        DomParser("(.*)", "", ""), MediaAdder() as Worker
                    ),
                    Scraper(DomParser("(.*)", "", ""), SetCreator()),

                    )
            )

        var scannerSerialized = Json.encodeToString(scanner)
        println(Json.encodeToString(scannerSerialized))

//        val scannerDesialized = Json.decodeFromString<Scanner>(scannerSerialized)
//        scannerSerialized = Json.encodeToString(scannerDesialized)
//        println(Json.encodeToString(scannerSerialized))
    }

    @Test
    fun `serialization and deserializtion work 4`() {

        val obj =
            Scraper(DomParser("(.*)", "", ""), SetCreator() )

        //            DomParser("(.*)", "", "")
//        SetCreator()

        val module = SerializersModule {
            polymorphic(Parser::class) {
                subclass(DomParser::class)
            }
            polymorphic(Worker::class) {
                subclass(SetCreator::class)
            }
        }
        val format = Json { serializersModule = module
            prettyPrint = true }

        var scannerSerialized = format.encodeToString(obj)
        println(Json.encodeToString(scannerSerialized))

        val scannerDesialized = format.decodeFromString<Scraper>(scannerSerialized)
        scannerSerialized = format.encodeToString(scannerDesialized)
        println(format.encodeToString(scannerSerialized))
    }

}
