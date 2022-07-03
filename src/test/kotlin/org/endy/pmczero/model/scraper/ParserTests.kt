package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.mockk.spyk
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import kotlin.test.assertEquals

class ParserTests {


    @Test
    fun test() {

        var text = "<html><title>TEST</title></html>"
        val domParser = DomParser("(.*)", "title", "")

        val elements = domParser.getElements(text, "http://test.com")

        assertEquals(1, elements?.size)
        assertEquals("TEST", elements[0])

    }

    @Test
    fun test2() {

        var text = "<html><a href='where'>Link</a></html>"

        val domParser = DomParser("(.*)", "a[href]", "abs:href")

        val elements = domParser.getElements(text, "http://test.com/afad/aaa/")

        assertEquals(1, elements?.size)
        assertEquals("http://test.com/afad/aaa/where", elements[0])

    }

}
