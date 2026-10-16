package org.endy.pmczero.ressource

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.service.ThumbnailService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * What `GET /api/msets/{id}/shares-files` answers, over http.
 *
 * Only the endpoint is under test: which sets share a file with a given one is `MsetService` and the
 * query under it, which are tested on their own.
 */
@WebMvcTest(controllers = [MsetRessource::class])
class MsetRessourceTests {

    @MockBean
    lateinit var msetService: MsetService

    @MockBean
    lateinit var scannerService: ScannerService

    @MockBean
    lateinit var locationService: LocationService

    @MockBean
    lateinit var thumbnailService: ThumbnailService

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `answers the sets that share a file with the one asked about`() {
        whenever(msetService.findSetsSharingFilesWith(1)).thenReturn(
            listOf(
                mset(2, "there"),
                mset(3, "and there")
            )
        )

        mvc.perform(get("/api/msets/1/shares-files"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").value(2))
            .andExpect(jsonPath("$[0].name").value("there"))
            .andExpect(jsonPath("$[1].id").value(3))
            .andExpect(jsonPath("$[1].name").value("and there"))
    }

    /**
     * A set that shares nothing is a successful answer and not an error: a gallery that was imported
     * once is the normal case, not something gone wrong.
     */
    @Test
    fun `answers an empty list for a set that shares nothing`() {
        whenever(msetService.findSetsSharingFilesWith(1)).thenReturn(emptyList())

        mvc.perform(get("/api/msets/1/shares-files"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    /** The set asked about is the one the url names, since every set has a different answer. */
    @Test
    fun `asks for the mset the url names`() {
        whenever(msetService.findSetsSharingFilesWith(7)).thenReturn(emptyList())

        mvc.perform(get("/api/msets/7/shares-files")).andExpect(status().isOk)

        verify(msetService).findSetsSharingFilesWith(7)
    }

    /**
     * A mistyped id must not be answered with an empty list, which is what the query behind this would
     * say about a set that does not exist and which reads as a clean result.
     */
    @Test
    fun `answers 404 for an mset that does not exist`() {
        whenever(msetService.findSetsSharingFilesWith(99))
            .thenAnswer { throw NotFoundException() }

        mvc.perform(get("/api/msets/99/shares-files")).andExpect(status().isNotFound)
    }

    /** The other endpoints are not dragged into this, so a set is not read twice per call. */
    @Test
    fun `does not read the mset itself to answer the shares`() {
        whenever(msetService.findSetsSharingFilesWith(1)).thenReturn(emptyList())

        mvc.perform(get("/api/msets/1/shares-files")).andExpect(status().isOk)

        verifyNoInteractions(thumbnailService, scannerService, locationService)
    }

    /** one set as the service hands it out, so this only says what the url does with it */
    private fun mset(id: Int, name: String) = Mset().apply { this.id = id; this.name = name }
}