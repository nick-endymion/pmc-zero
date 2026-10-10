package org.endy.pmczero.model.scraper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTests {

    @Test
    fun `DomParser returns the text of the selected tag`() {

        val text = "<html><title>TEST</title></html>"
        val domParser = DomParser("(.*)", "title", "")

        val elements = domParser.getElements(text, "http://test.com")

        assertEquals(1, elements.size)
        assertEquals("TEST", elements[0])
    }

    @Test
    fun `DomParser resolves a relative attribute against the base uri`() {

        val text = "<html><a href='where'>Link</a></html>"

        val domParser = DomParser("(.*)", "a[href]", "abs:href")

        val elements = domParser.getElements(text, "http://test.com/afad/aaa/")

        assertEquals(1, elements.size)
        assertEquals("http://test.com/afad/aaa/where", elements[0])
    }

    @Test
    fun `DomParser returns every match, in document order`() {

        val text = """
            <html><body>
            <a href="one">First</a>
            <a href="two">Second</a>
            <a href="three">Third</a>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*)", "a", "abs:href")
            .getElements(text, "http://test.com/")

        assertEquals(listOf("http://test.com/one", "http://test.com/two", "http://test.com/three"), elements)
    }

    @Test
    fun `DomParser drops the elements the regex does not match`() {

        val text = """
            <html><body>
            <img src="cat.jpg">
            <img src="dog.png">
            <img src="bird.gif">
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*\\.jpg)", "img", "src").getElements(text, "http://test.com/")

        assertEquals(listOf("cat.jpg"), elements)
    }

    @Test
    fun `DomParser drops elements missing the attribute when the regex needs a value`() {

        // `attr` answers an empty string for an absent attribute, so this pins that a
        // caller wanting only real urls has to ask for a non-empty match.
        val text = "<html><body><a href='a.html'>with</a><a>without</a></body></html>"

        val elements = DomParser("(.+)", "a", "href").getElements(text, "http://test.com/")

        assertEquals(listOf("a.html"), elements)
    }

    @Test
    fun `DomParser returns nothing when the tag is absent`() {

        val elements = DomParser("(.*)", "img", "src").getElements("<html><body>no images</body></html>", "http://test.com/")

        assertTrue(elements.isEmpty())
    }

    @Test
    fun `DomParser normalises the whitespace of the extracted text`() {

        val text = "<html><body><p>  hello \n\n  world  </p></body></html>"

        val elements = DomParser("(.*)", "p", "").getElements(text, "http://test.com/")

        assertEquals(listOf("hello world"), elements)
    }

    @Test
    fun `RegexParser returns the first group of every match`() {

        val text = """<img src="a.png"><img src="b.png">"""

        val elements = RegexParser("""src="(.*?)"""").getElements(text, "http://test.com/")

        assertEquals(listOf("a.png", "b.png"), elements)
    }

    @Test
    fun `RegexParser returns nothing when the pattern does not occur`() {

        val elements = RegexParser("""src="(.*?)"""").getElements("<html>plain</html>", "http://test.com/")

        assertTrue(elements.isEmpty())
    }

    @Test
    fun `PassThroughParser returns the text unchanged`() {

        val text = "<html><body>anything goes</body></html>"

        val elements = PassThroughParser().getElements(text, "http://test.com/")

        assertEquals(listOf(text), elements)
    }

    @Test
    fun `every parser ignores the base uri it cannot use`() {

        val text = "<html><body><a href='x.html'>link</a></body></html>"
        val baseUri = "http://test.com/"

        assertEquals(listOf("x.html"), RegexParser("""href='(.*?)'""").getElements(text, baseUri))
        assertEquals(listOf(text), PassThroughParser().getElements(text, baseUri))
    }

    // -------------------------------------------------------------------------------------
    // Answers that say the same thing twice
    // -------------------------------------------------------------------------------------

    /**
     * The case this is for: a page that shows the same image in a teaser and in the gallery, which
     * used to become two media for one file and a second download of it.
     */
    @Test
    fun `DomParser says an element the page holds twice once`() {
        val text = """
            <html><body>
            <img src="bilder/a.jpg">
            <img src="bilder/a.jpg">
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.+)", "img", "abs:src").getElements(text, "http://test.com/")

        assertEquals(listOf("http://test.com/bilder/a.jpg"), elements)
    }

    /** The first of a run of equals is the one kept, since the answer is in document order. */
    @Test
    fun `DomParser keeps the first of equal elements`() {
        val text = """
            <html><body>
            <a href="one">First</a>
            <a href="two">Second</a>
            <a href="one">The same link again</a>
            <a href="three">Third</a>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*)", "a", "abs:href").getElements(text, "http://test.com/")

        assertEquals(
            listOf("http://test.com/one", "http://test.com/two", "http://test.com/three"),
            elements
        )
    }

    /** Two texts that are equal are one answer, which is what makes the title of a page safe to read. */
    @Test
    fun `DomParser says two elements with the same text once`() {
        val text = """
            <html><body>
            <div class="caption">Gallery</div>
            <div class="caption">Gallery</div>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.+)", ".caption", "").getElements(text, "http://test.com/")

        assertEquals(listOf("Gallery"), elements)
    }

    /**
     * Equal as a string and nothing more: a parser answers strings, so a relative and an absolute
     * url are two answers here even where they are one page.
     */
    @Test
    fun `DomParser says a relative and an absolute url apart`() {
        val text = """
            <html><body>
            <a href="/gallery/1">One way</a>
            <a href="http://test.com/gallery/1">The same page</a>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*)", "a", "href").getElements(text, "http://test.com/")

        assertEquals(listOf("/gallery/1", "http://test.com/gallery/1"), elements)
    }

    /** An element the page holds with the same nothing three times is still one answer. */
    @Test
    fun `DomParser says equal empty elements once`() {
        val text = "<html><body><a>one</a><a>two</a><a>three</a></body></html>"

        // no attribute on any of them, so all three read as the empty string
        val elements = DomParser("(.*)", "a", "href").getElements(text, "http://test.com/")

        assertEquals(listOf(""), elements)
    }

    @Test
    fun `RegexParser says a match the text holds twice once`() {
        val text = """<img src="a.png"><img src="a.png"><img src="b.png">"""

        val elements = RegexParser("""src="(.*?)"""").getElements(text, "http://test.com/")

        assertEquals(listOf("a.png", "b.png"), elements)
    }

    /** One element, so never a duplicate: pinned so that the pass through stays a pass through. */
    @Test
    fun `PassThroughParser says the text once`() {
        val text = "<html><body><a href='x.html'>x.html</a>anything goes</body></html>"

        assertEquals(listOf(text), PassThroughParser().getElements(text, "http://test.com/"))
    }

    /**
     * The same rule for every parser, which is what one place in [Parser] is for: a parser written
     * later gets it by being a parser rather than by remembering.
     */
    @Test
    fun `every parser says an answer it would say twice once`() {
        val withTheAnswerTwice = """
            <html><head><title>TEST</title></head><body>
            <a href="same">one</a><a href="same">two</a>
            <img src="same.png"><img src="same.png">
            </body></html>
        """.trimIndent()

        val parsers = listOf(
            DomParser("(.*)", "title", ""),
            DomParser("(.*)", "a", "abs:href"),
            DomParser("(.*)", "img", "abs:src"),
            RegexParser("""href="(.*?)""""),
            RegexParser("""src="(.*?)""""),
            PassThroughParser()
        )

        for (parser in parsers) {
            val elements = parser.getElements(withTheAnswerTwice, "http://test.com/")

            assertEquals(
                elements.distinct(),
                elements,
                "${parser::class.simpleName} answered $elements"
            )
        }
    }
}
