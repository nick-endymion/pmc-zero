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
}
