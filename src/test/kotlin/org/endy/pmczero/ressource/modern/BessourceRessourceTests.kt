package org.endy.pmczero.ressource.modern

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.service.BessourceFiles
import org.endy.pmczero.service.MediaService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import java.net.URI
import kotlin.test.assertEquals

/**
 * What the url of a served file does, over http and through the real request mapping rather than
 * through a call of the method.
 *
 * The mapping is the thing worth testing and the thing a unit test of the method cannot see: whether
 * `bilder/a.jpg` arrives as one name or as two segments is decided by spring and by the path matcher
 * this application runs with, not by this code. So the request goes out as a caller would write it.
 *
 * The bytes come from a real file in a temp folder, since what is answered is a file: a length and a
 * content type a mocked one would only pretend to have.
 */
@WebMvcTest(controllers = [BessourceRessource::class])
class BessourceRessourceTests {

    @MockBean
    lateinit var bessourceFiles: BessourceFiles

    @MockBean
    lateinit var mediaService: MediaService

    @MockBean
    lateinit var locationRepository: LocationRepository

    @Autowired
    lateinit var mvc: MockMvc

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------------------------
    // What it serves
    // -------------------------------------------------------------------------------------

    @Test
    fun `serves the file a name stands for`() {
        givenFile("bilder/a.jpg")

        mvc.perform(get("/main/1/bilder/a.jpg"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(content().bytes(BYTES))
            .andExpect(header().longValue("Content-Length", BYTES.size.toLong()))
    }

    /**
     * The whole reason the shape is a path and not a name: a bessource name is a path below the
     * location, and it has to arrive as one name rather than as segments a caller would have to quote.
     */
    @Test
    fun `serves a file whose name names directories of its own`() {
        givenFile("imagegap4/abc/984580928.jpg")

        mvc.perform(get("/main/1/imagegap4/abc/984580928.jpg"))
            .andExpect(status().isOk)
            .andExpect(content().bytes(BYTES))

        verify(bessourceFiles).mainFileOf(1, "imagegap4/abc/984580928.jpg")
    }

    @Test
    fun `serves a file directly below the location`() {
        givenFile("a.jpg")

        mvc.perform(get("/main/1/a.jpg")).andExpect(status().isOk)

        verify(bessourceFiles).mainFileOf(1, "a.jpg")
    }

    @Test
    fun `serves a file whose name is several directories deep`() {
        givenFile("a/b/c/d/e.jpg")

        mvc.perform(get("/main/1/a/b/c/d/e.jpg")).andExpect(status().isOk)

        verify(bessourceFiles).mainFileOf(1, "a/b/c/d/e.jpg")
    }

    /** A space in a stored name is written as `%20` and arrives as the space again. */
    @Test
    fun `serves a file whose name has a space in it`() {
        givenFile("bilder/mein bild.jpg")

        mvc.perform(get(URI.create("/main/1/bilder/mein%20bild.jpg")))
            .andExpect(status().isOk)
            .andExpect(content().bytes(BYTES))

        verify(bessourceFiles).mainFileOf(1, "bilder/mein bild.jpg")
    }

    /** An umlaut is written percent encoded and is decoded as utf-8, which is what a name is written in. */
    @Test
    fun `serves a file whose name has an umlaut in it`() {
        givenFile("bilder/grüße.jpg")

        mvc.perform(get(URI.create("/main/1/bilder/gr%C3%BC%C3%9Fe.jpg"))).andExpect(status().isOk)

        verify(bessourceFiles).mainFileOf(1, "bilder/grüße.jpg")
    }

    /** An extension nothing knows is delivered as bytes rather than refused. */
    @Test
    fun `serves a file whose extension nothing knows as octet stream`() {
        givenFile("bilder/a.keinformat")

        mvc.perform(get("/main/1/bilder/a.keinformat"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/octet-stream"))
    }

    /** The storage of the url is the one asked for, since two storages may hold the same name. */
    @Test
    fun `serves the file of the storage the url names`() {
        givenFile("bilder/a.jpg")

        mvc.perform(get("/main/42/bilder/a.jpg")).andExpect(status().isOk)

        verify(bessourceFiles).mainFileOf(42, "bilder/a.jpg")
    }

    // -------------------------------------------------------------------------------------
    // What it refuses
    // -------------------------------------------------------------------------------------

    /**
     * Nothing at that name, which is also the answer for a name that climbs out of the location: the
     * service resolves both to null and this endpoint must not tell the two apart either.
     */
    @Test
    fun `answers 404 for a name the service does not resolve`() {
        whenever(bessourceFiles.mainFileOf(1, "../ausserhalb.jpg")).thenReturn(null)

        mvc.perform(get(URI.create("/main/1/..%2Fausserhalb.jpg")))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
    }

    @Test
    fun `answers 404 for a name that is not there`() {
        whenever(bessourceFiles.mainFileOf(1, "bilder/weg.jpg")).thenReturn(null)

        mvc.perform(get("/main/1/bilder/weg.jpg")).andExpect(status().isNotFound)
    }

    @Test
    fun `answers 404 for a storage that does not exist`() {
        whenever(bessourceFiles.mainFileOf(99, "bilder/a.jpg"))
            .thenAnswer { throw NotFoundException() }

        mvc.perform(get("/main/99/bilder/a.jpg")).andExpect(status().isNotFound)
    }

    /** A folder is not a file, so a name that lands on one answers nothing. */
    @Test
    fun `answers 404 for a name that names a folder`() {
        whenever(bessourceFiles.mainFileOf(1, "bilder")).thenReturn(null)

        mvc.perform(get("/main/1/bilder")).andExpect(status().isNotFound)
    }

    /** `/main/1` names no file at all, and is not a listing of the location either. */
    @Test
    fun `answers 404 when no name is given`() {
        mvc.perform(get("/main/1")).andExpect(status().isNotFound)
    }

    /** A url that cannot be decoded is a name that cannot be looked for, not a container error. */
    @Test
    fun `answers 404 for a name the url does not spell properly`() {
        // asked for directly rather than over http, since a malformed escape is not a uri and the
        // container would refuse the request before it reached here
        val request = MockHttpServletRequest().apply { requestURI = "/main/1/bilder/a%2.jpg" }

        assertThrows<NotFoundException> {
            bessourceRessource().file(1, request)
        }
    }

    /** A 404 is a 404 and not the html error page of the container, so a client reading json gets json. */
    @Test
    fun `answers a 404 that is not the container error page`() {
        whenever(bessourceFiles.mainFileOf(1, "weg.jpg")).thenReturn(null)

        val answer = mvc.perform(get("/main/1/weg.jpg")).andReturn().response

        assertEquals("{\"error\":\"not found\"}", answer.contentAsString)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** the endpoint on its own, for the one case that cannot be asked for over http */
    private fun bessourceRessource() = BessourceRessource(locationRepository, mediaService, bessourceFiles)

    /** a file of the location, which the service hands back for whatever name is asked for */
    private fun givenFile(name: String): File {
        val file = File(tempDir, name).apply { parentFile?.mkdirs() }
        file.writeBytes(BYTES)

        whenever(bessourceFiles.mainFileOf(1, name)).thenReturn(file)
        whenever(bessourceFiles.mainFileOf(42, name)).thenReturn(file)

        return file
    }

    private companion object {
        /** the content of a served file, so a response is bytes rather than a description of bytes */
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    }
}