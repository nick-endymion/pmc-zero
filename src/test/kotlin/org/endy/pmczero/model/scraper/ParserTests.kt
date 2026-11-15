package org.endy.pmczero.model.scraper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTests {

    @Test
    fun `DomParser returns the text of the selected tag`() {

        val text = "<html><title>TEST</title></html>"
        val domParser = DomParser("(.*)", "title", "")

        val elements = domParser.getElements(listOf(text), "http://test.com")

        assertEquals(1, elements.size)
        assertEquals("TEST", elements[0])
    }

    @Test
    fun `DomParser resolves a relative attribute against the base uri`() {

        val text = "<html><a href='where'>Link</a></html>"

        val domParser = DomParser("(.*)", "a[href]", "abs:href")

        val elements = domParser.getElements(listOf(text), "http://test.com/afad/aaa/")

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
            .getElements(listOf(text), "http://test.com/")

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

        val elements = DomParser("(.*\\.jpg)", "img", "src").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("cat.jpg"), elements)
    }

    @Test
    fun `DomParser drops elements missing the attribute when the regex needs a value`() {

        // `attr` answers an empty string for an absent attribute, so this pins that a
        // caller wanting only real urls has to ask for a non-empty match.
        val text = "<html><body><a href='a.html'>with</a><a>without</a></body></html>"

        val elements = DomParser("(.+)", "a", "href").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("a.html"), elements)
    }

    /**
 * A fragment is a reference to a place on the page it is written in, so it resolves against the base uri
 * like any other relative reference and answers the whole address rather than the bare fragment.
 */
@Test
    fun `DomParser resolves a fragment against the base uri`() {

        val text = """<html><a rel="history" href="#15"><img src="x.jpg"></a></html>"""

        val elements = DomParser("(.*)", "a[href]", "abs:href")
            .getElements(listOf(text), "http://example.org/user/12345/index.php")

        assertEquals(listOf("http://example.org/user/12345/index.php#15"), elements)
    }

    /**
     * Which is what makes the plain attribute useless for this: `#15` is a reference to nothing outside
     * the context of the element, and a caller reading it has to know which page it came from.
     */
    @Test
    fun `DomParser answers a bare fragment for a plain attribute`() {

        val text = """<html><a rel="history" href="#15"><img src="x.jpg"></a></html>"""

        val elements = DomParser("(.*)", "a[href]", "href")
            .getElements(listOf(text), "http://example.org/user/12345/index.php")

        assertEquals(listOf("#15"), elements)
    }

    /**
     * `:last-child` is relative to a parent, so a selector that says "the last link of the page" does
     * not exist in css: it answers the last of each list a page writes.
     */
    @Test
    fun `DomParser answers the last element of each parent for a last child selector`() {

        val text = """
            <html><body>
            <div class="head"><a href="/start.html">Start</a></div>
            <div class="history"><a rel="history" href="#14">fourteen</a><a rel="history" href="#15">fifteen</a></div>
            <div class="foot"><a href="/kontakt.html">Kontakt</a></div>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*)", "a[href]:last-child", "abs:href")
            .getElements(listOf(text), "http://example.org/start.html")

        assertEquals(
            listOf(
                "http://example.org/start.html",
                "http://example.org/start.html#15",
                "http://example.org/kontakt.html"
            ),
            elements
        )
    }

    /**
     * And narrowing the selector to what identifies these anchors is what makes it answer one, since a
     * page has one block of them.
     */
    @Test
    fun `DomParser answers one element for a narrow last child selector`() {

        val text = """
            <html><body>
            <div class="head"><a href="/start.html">Start</a></div>
            <div class="history">
              <a class="expp" rel="history" href="#14"><img src="14.jpg"></a>
              <a class="expp" rel="history" href="#15"><img src="15.jpg"></a>
            </div>
            <div class="foot"><a href="/kontakt.html">Kontakt</a></div>
            </body></html>
        """.trimIndent()

        val elements = DomParser("(.*)", "a.expp[rel=history]:last-child", "abs:href")
            .getElements(listOf(text), "http://example.org/user/12345/index.php")

        assertEquals(listOf("http://example.org/user/12345/index.php#15"), elements)
    }

    /**
 * The same elements as a [DomParser] would have answered and only the last of them, which is what a
 * selector cannot say on its own: `:last-child` is about a parent.
 */
@Test
    fun `DomElememenParser answers the last element of a page`() {

        val text = """
            <html><body>
            <a href="/one.html">One</a>
            <a href="/two.html">Two</a>
            <a href="/three.html">Three</a>
            </body></html>
        """.trimIndent()

        val last = DomLastElememenParser("(.*)", "a[href]", "abs:href").getElements(listOf(text), "http://example.org/")

        val all = DomParser("(.*)", "a[href]", "abs:href").getElements(listOf(text), "http://example.org/")

        assertEquals(listOf("http://example.org/three.html"), last)
        assertEquals(all.last(), last.single(), "the last of what the DomParser answered")
    }

    /**
     * Of the document rather than of each block, which is the difference from a `:last-child` selector
     * and the reason this is a parser: on this page the anchors are each alone in a wrapper, so every
     * one of them is a last child.
     */
    @Test
    fun `DomElememenParser answers one element for a page whose matches are wrapped one by one`() {

        val text = """
            <html><body>
            <div class="head"><a href="/start.html">Start</a></div>
            <div class="thumb"><a href="/one.html">One</a></div>
            <div class="thumb"><a href="/two.html">Two</a></div>
            <div class="foot"><a href="/kontakt.html">Kontakt</a></div>
            </body></html>
        """.trimIndent()

        val elements = DomLastElememenParser("(.*)", "a[href]", "abs:href")
            .getElements(listOf(text), "http://example.org/")

        assertEquals(listOf("http://example.org/kontakt.html"), elements)
    }

    /** The same selector, the same attribute and the same regex: only the answer differs. */
    @Test
    fun `DomElememenParser answers one history link of a page of them`() {

        val text = """
            <html><body>
            <a class="expp" rel="history" href="#14"><img src="/mini/14.jpg"></a>
            <a class="expp" rel="history" href="#15"><img src="/mini/15.jpg"></a>
            </body></html>
        """.trimIndent()

        val elements = DomLastElememenParser("(.*)", "a.expp[rel=history]", "abs:href")
            .getElements(listOf(text), "http://example.org/user/12345/index.php")

        assertEquals(listOf("http://example.org/user/12345/index.php#15"), elements)
    }

    /** The regex is applied first, so the last of the matching elements is the last one there is. */
    @Test
    fun `DomElememenParser answers the last element the regex matches`() {

        val text = """
            <html><body>
            <a href="/one.jpg">One</a>
            <a href="/notes.txt">Two</a>
            <a href="/three.jpg">Three</a>
            </body></html>
        """.trimIndent()

        val elements = DomLastElememenParser("(.+\\.jpg)", "a[href]", "abs:href")
            .getElements(listOf(text), "http://example.org/")

        assertEquals(listOf("http://example.org/three.jpg"), elements)
    }

    @Test
    fun `DomElememenParser answers nothing from a page without a match`() {

        val elements = DomLastElememenParser("(.*)", "img[src]", "abs:src")
            .getElements(listOf("<html><body>no images</body></html>"), "http://example.org/")

        assertTrue(elements.isEmpty())
    }

    /** One element is the last one as much as three are. */
    @Test
    fun `DomElememenParser answers the only element of a page`() {

        val text = """<html><body><a href="/only.html">Only</a></body></html>"""

        val elements = DomLastElememenParser("(.*)", "a[href]", "abs:href").getElements(listOf(text), "http://example.org/")

        assertEquals(listOf("http://example.org/only.html"), elements)
    }

    @Test
    fun `DomParser returns nothing when the tag is absent`() {

        val elements = DomParser("(.*)", "img", "src").getElements(listOf("<html><body>no images</body></html>"), "http://test.com/")

        assertTrue(elements.isEmpty())
    }

    @Test
    fun `DomParser normalises the whitespace of the extracted text`() {

        val text = "<html><body><p>  hello \n\n  world  </p></body></html>"

        val elements = DomParser("(.*)", "p", "").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("hello world"), elements)
    }

    @Test
    fun `RegexParser returns the first group of every match`() {

        val text = """<img src="a.png"><img src="b.png">"""

        val elements = RegexParser("""src="(.*?)"""").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("a.png", "b.png"), elements)
    }

    @Test
    fun `RegexParser returns nothing when the pattern does not occur`() {

        val elements = RegexParser("""src="(.*?)"""").getElements(listOf("<html>plain</html>"), "http://test.com/")

        assertTrue(elements.isEmpty())
    }

    @Test
    fun `PassThroughParser returns the text unchanged`() {

        val text = "<html><body>anything goes</body></html>"

        val elements = PassThroughParser().getElements(listOf(text), "http://test.com/")

        assertEquals(listOf(text), elements)
    }

    @Test
    fun `every parser ignores the base uri it cannot use`() {

        val text = "<html><body><a href='x.html'>link</a></body></html>"
        val baseUri = "http://test.com/"

        assertEquals(listOf("x.html"), RegexParser("""href='(.*?)'""").getElements(listOf(text), baseUri))
        assertEquals(listOf(text), PassThroughParser().getElements(listOf(text), baseUri))
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

        val elements = DomParser("(.+)", "img", "abs:src").getElements(listOf(text), "http://test.com/")

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

        val elements = DomParser("(.*)", "a", "abs:href").getElements(listOf(text), "http://test.com/")

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

        val elements = DomParser("(.+)", ".caption", "").getElements(listOf(text), "http://test.com/")

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

        val elements = DomParser("(.*)", "a", "href").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("/gallery/1", "http://test.com/gallery/1"), elements)
    }

    /** An element the page holds with the same nothing three times is still one answer. */
    @Test
    fun `DomParser says equal empty elements once`() {
        val text = "<html><body><a>one</a><a>two</a><a>three</a></body></html>"

        // no attribute on any of them, so all three read as the empty string
        val elements = DomParser("(.*)", "a", "href").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf(""), elements)
    }

    @Test
    fun `RegexParser says a match the text holds twice once`() {
        val text = """<img src="a.png"><img src="a.png"><img src="b.png">"""

        val elements = RegexParser("""src="(.*?)"""").getElements(listOf(text), "http://test.com/")

        assertEquals(listOf("a.png", "b.png"), elements)
    }

    /** One element, so never a duplicate: pinned so that the pass through stays a pass through. */
    @Test
    fun `PassThroughParser says the text once`() {
        val text = "<html><body><a href='x.html'>x.html</a>anything goes</body></html>"

        assertEquals(listOf(text), PassThroughParser().getElements(listOf(text), "http://test.com/"))
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
            val elements = parser.getElements(listOf(withTheAnswerTwice), "http://test.com/")

            assertEquals(
                elements.distinct(),
                elements,
                "${parser::class.simpleName} answered $elements"
            )
        }
    }

    /** One text in, the text out: the pass-through case of the numbered answer. */
    @Test
    fun `SpecialFabParser answers the one text of a list of one`() {

        val text = "<html><body>anything goes</body></html>"

        val elements = SpecialFabParser().getElements(listOf(text), "http://test.com/")

        assertEquals(listOf(text), elements)
    }

    /**
     * One answer per text the list holds, and every answer out of the first of them: the first
     * answer is the text itself, the ones behind it are the text with the number joined on. What
     * the texts behind the first hold is not read, the same first-text reading every parser of
     * this package does.
     */
    @Test
    fun `SpecialFabParser answers the first text once per text the list holds`() {

        val elements = SpecialFabParser().getElements(listOf("a", "b", "c"), "http://test.com/")

        assertEquals(listOf("a", "a#1", "a#2"), elements)
    }

    @Test
    fun `SpecialFabParser answers nothing for an empty list`() {

        assertTrue(SpecialFabParser().getElements(emptyList(), "http://test.com/").isEmpty())
    }
}
