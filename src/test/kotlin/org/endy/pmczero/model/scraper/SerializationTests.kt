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
            DomLastElememenParser("(.*)", "a[href]", "abs:href"),
            DomFirstElememenParser("(.*)", "a[href]", "abs:href"),
            RegexParser("(.*fa.*)"),
            PassThroughParser(),
            SequenceParser(listOf(DomParser("(.*)", "img", "abs:src"), RegexParser("(.*fa.*)"))),
            NumberedParser(5),
            SpecialFabParser()
        )
        val serialNames = listOf("dom", "domLastElement", "domFirstElement", "regex", "passThrough", "sequence", "numbered", "specialFab")

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
                is DomLastElememenParser -> {
                    val d = assertIs<DomLastElememenParser>(decoded)
                    assertEquals(parser.regex, d.regex)
                    assertEquals(parser.tag, d.tag)
                    assertEquals(parser.attribute, d.attribute)
                }
                is DomFirstElememenParser -> {
                    val d = assertIs<DomFirstElememenParser>(decoded)
                    assertEquals(parser.regex, d.regex)
                    assertEquals(parser.tag, d.tag)
                    assertEquals(parser.attribute, d.attribute)
                }
                is RegexParser -> assertEquals(parser.regex, assertIs<RegexParser>(decoded).regex)
                is PassThroughParser -> assertIs<PassThroughParser>(decoded)
                is SequenceParser -> assertEquals(
                    parser.parsers.map { it::class },
                    assertIs<SequenceParser>(decoded).parsers.map { it::class }
                )
                is NumberedParser -> assertEquals(parser.n, assertIs<NumberedParser>(decoded).n)
                is SpecialFabParser -> assertIs<SpecialFabParser>(decoded)
            }
        }
    }

    /**
     * Three fields and nothing else, i.e. the parser that does the parsing is not in the stored json.
 *
 * kotlinx.serialization writes every property that has a backing field, so a parser that kept its
 * [DomParser] in a field would store it as a second nested parser object: the same three values twice,
 * which a reader of `a.scanner.serialization` would have to know to ignore.
 *
 * Pinned as the exact json rather than as the absence of one field, so a second object of whatever kind
 * is a failure too.
 */
@Test
    fun `a stored last element parser carries its three fields and nothing else`() {

        val json = Json.encodeToString(
            Scraper(DomLastElememenParser("(.*)", "a.expp[rel=history]", "abs:href"), FoundElementsWorker(1))
        )

        assertEquals(
            """{"parser":{"type":"domLastElement","regex":"(.*)","tag":"a.expp[rel=history]","attribute":"abs:href"},"worker":{"type":"foundElements","level":1}}""",
            json
        )
    }

    /**
     * A stored scanner that keeps the last element keeps it, i.e. the parser comes back as itself and
     * not as the plain [DomParser] it reads like in the json.
     *
     * The symptom of getting this wrong is a scanner that works and answers every element again: the
     * three fields are the same, so only the behaviour of the stored scraper tells the two apart.
     */
    @Test
    fun `a stored scraper keeps the parser that answers the last element`() {

        val json = Json.encodeToString(
            Scraper(DomLastElememenParser("(.*)", "a.expp[rel=history]", "abs:href"), FoundElementsWorker(1))
        )

        assertTrue(json.contains(""""type":"domLastElement""""), "domLastElement expected in $json")

        val decoded = Json.decodeFromString<Scraper>(json).parser

        val parser = assertIs<DomLastElememenParser>(decoded)
        assertEquals("a.expp[rel=history]", parser.tag)
        assertEquals(
            listOf("http://example.org/index.php#15"),
            parser.getElements(
                listOf(
                    """<a class="expp" rel="history" href="#14">14</a>
                       <a class="expp" rel="history" href="#15">15</a>"""
                ),
                "http://example.org/index.php"
            )
        )
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

    /**
     * [FileDownloader] round trips under its serial name, so it can be configured in a scanner row
     * like the other workers.
     *
     * It is the one worker with no parameters of its own, everything it needs comes from the
     * [org.endy.pmczero.model.ScanningKontext], which is exactly why the path lives there rather than
     * on the worker: a stored scraper would otherwise carry a path that only meant anything when it
     * was configured.
     */
    @Test
    fun `the file downloader round trips under its serial name`() {

        val json = Json.encodeToString(Scraper(PassThroughParser(), FileDownloader()))

        assertTrue(json.contains(""""type":"fileDownloader""""), "fileDownloader expected in $json")

        val decoded = Json.decodeFromString<Scraper>(json)

        assertIs<FileDownloader>(decoded.worker)
        assertIs<PassThroughParser>(decoded.parser)
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
