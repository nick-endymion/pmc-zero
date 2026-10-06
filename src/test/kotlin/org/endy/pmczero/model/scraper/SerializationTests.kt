package org.endy.pmczero.model.scraper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SerializationTests {

    /**
     * Every parser round trips through a plain [Json], under its own serial name.
     *
     * This is the test that keeps [Parser] sealed. Sealed is what lets kotlinx.serialization resolve
     * the three subclasses on its own; as an abstract class they would need every caller to register
     * them in a module, and a [Json] that carries none would fail on the first scraper it met. The
     * serial names are the wire format of a.scanner.serialization, so they are asserted rather than
     * left to whatever the plugin picks.
     */
    @Test
    fun `every parser round trips under its serial name`() {

        val parsers: List<Parser> = listOf(
            DomParser("(.*)", "img", "abs:src"),
            RegexParser("(.*fa.*)"),
            PassThroughParser()
        )
        val serialNames = listOf("dom", "regex", "passThrough")

        for ((parser, serialName) in parsers.zip(serialNames)) {

            val json = Json.encodeToString(Scraper(parser, SetCreator()))

            assertTrue(
                json.contains(""""type":"$serialName""""),
                "$serialName expected in $json"
            )

            // fields rather than the object, since a parser is a plain class without an equals
            val decoded = Json.decodeFromString<Scraper>(json).parser
            when (parser) {
                is DomParser -> {
                    val d = assertIs<DomParser>(decoded)
                    assertEquals(parser.regex, d.regex)
                    assertEquals(parser.tag, d.tag)
                    assertEquals(parser.attribute, d.attribute)
                }
                is RegexParser -> assertEquals(parser.regex, assertIs<RegexParser>(decoded).regex)
                is PassThroughParser -> assertIs<PassThroughParser>(decoded)
            }
        }
    }

    /**
     * The whole tree of a scanner survives, i.e. a parser nested inside a worker nested inside
     * another scraper, and the nested [DomParser] keeps the fields it was built with.
     */
    @Test
    fun `a nested scraper keeps its parser and its worker`() {

        val inner = DomParser("(.*)", "img", "abs:src")
        val scanner = Scraper(
            RegexParser("(.*fa.*)"),
            StructuredWorker(
                true,
                listOf(
                    Scraper(DomParser("(.*)", "title", ""), SetCreator()),
                    Scraper(inner, MediaAdder())
                )
            )
        )

        val json = Json.encodeToString(scanner)
        val decoded = Json.decodeFromString<Scraper>(json)

        assertIs<RegexParser>(decoded.parser)

        val structured = assertIs<StructuredWorker>(decoded.worker)
        assertTrue(structured.download)
        assertEquals(2, structured.scrapers.size)

        val nested = assertIs<DomParser>(structured.scrapers[1].parser)
        assertEquals(inner.regex, nested.regex)
        assertEquals(inner.tag, nested.tag)
        assertEquals(inner.attribute, nested.attribute)
        assertIs<MediaAdder>(structured.scrapers[1].worker)
        assertIs<SetCreator>(structured.scrapers[0].worker)

        // and the second round trip is byte for byte the first, so re-saving a scanner cannot
        // drift the stored json
        assertEquals(json, Json.encodeToString(decoded))
    }

    /**
     * A scanner row written before [Parser] became sealed still loads.
     *
     * a.scanner.serialization is json that [org.endy.pmczero.service.ScannerService.save] wrote
     * through the module based `PolymorphicSerializer`, and a sealed base writes the same shape with
     * the same discriminator key, so old rows do not have to be migrated. Pinned here because the
     * only symptom of getting it wrong would be a scanner that fails to start on a database that
     * was populated before the change.
     */
    @Test
    fun `a scanner serialized before Parser became sealed still loads`() {

        val stored = """
            {"parser":{"type":"regex","regex":"(.*fa.*)"},"worker":{"type":"structured","download":true,
             "scrapers":[{"parser":{"type":"dom","regex":"(.*)","tag":"title","attribute":""},
                          "worker":{"type":"setCreator"}},
                         {"parser":{"type":"dom","regex":"(.+)","tag":"img","attribute":"abs:src"},
                          "worker":{"type":"mediaAdder"}}]}}
        """.trimIndent()

        val decoded = Json { prettyPrint = true }.decodeFromString<Scraper>(stored)

        assertIs<RegexParser>(decoded.parser)
        val structured = assertIs<StructuredWorker>(decoded.worker)
        assertTrue(structured.download)
        assertEquals(2, structured.scrapers.size)
        assertEquals("abs:src", assertIs<DomParser>(structured.scrapers[1].parser).attribute)
        assertIs<MediaAdder>(structured.scrapers[1].worker)
    }

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
