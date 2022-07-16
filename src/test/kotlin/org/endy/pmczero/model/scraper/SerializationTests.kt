package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import org.junit.jupiter.api.Test

class SerializationTests {

    @Test
    fun `serialization and deserializtion work`() {

        val scanner = Scanner(
//           SimpleParser("(.*fa.*)", "a"),
            DomParser("(.*)", "ad", "adfa"),
            SetCreator()
        )

        var scannerSerialized = Json.encodeToString<Scanner>(scanner)
        println(Json.encodeToString(scannerSerialized))

        val scannerDesialized = Json.decodeFromString<Scanner>(scannerSerialized)
        scannerSerialized = Json.encodeToString(scannerDesialized)
        println(Json.encodeToString(scannerSerialized))
    }

    @Test
    fun `serialization and deserializtion work 2`() {

        val scanner = Scanner(
            SimpleParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scanner(DomParser("(.*)", "", ""), SetCreator()),
                    Scanner(
                        DomParser("(.*)", "", ""), MediaAdder()
                    )
                )
            )
        )

        var scannerSerialized = Json.encodeToString(scanner)
        println(Json.encodeToString(scannerSerialized))

        val scannerDesialized = Json.decodeFromString<Scanner>(scannerSerialized)
        scannerSerialized = Json.encodeToString(scannerDesialized)
        println(Json.encodeToString(scannerSerialized))
    }

    @Test
    fun `serialization and deserializtion work 3`() {

        val scanner =
            StructuredWorker(
                true,
                listOf(
                    Scanner(
                        DomParser("(.*)", "", ""), MediaAdder() as Worker
                    ),
                    Scanner(DomParser("(.*)", "", ""), SetCreator()),

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
            Scanner(DomParser("(.*)", "", ""), SetCreator() )

        //            DomParser("(.*)", "", "")
//        SetCreator()

        val module = SerializersModule {
            polymorphic(HtmlParser::class) {
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

        val scannerDesialized = format.decodeFromString<Scanner>(scannerSerialized)
        scannerSerialized = format.encodeToString(scannerDesialized)
        println(format.encodeToString(scannerSerialized))
    }

}
