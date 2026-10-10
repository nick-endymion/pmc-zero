package org.endy.pmczero.ressource

import org.endy.pmczero.service.FailedDownloadService
import org.endy.pmczero.to.FailedDownloadRunTO
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * What `POST /api/downloads/retry-failed` answers.
 *
 * The endpoint is a call on a service and a mapping, so that is all this is about: what the retry
 * does with what it finds is [FailedDownloadService] and its own tests.
 */
@WebMvcTest(controllers = [DownloadRessource::class])
class DownloadRessourceTests {

    @MockBean
    lateinit var failedDownloadService: FailedDownloadService

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `answers what the retry wrote`() {
        whenever(failedDownloadService.retryAll()).thenReturn(
            FailedDownloadRunTO(attempted = 3, downloaded = 2, skipped = 0, failed = 1)
        )

        mvc.perform(post("/api/downloads/retry-failed"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.attempted").value(3))
            .andExpect(jsonPath("$.downloaded").value(2))
            .andExpect(jsonPath("$.skipped").value(0))
            .andExpect(jsonPath("$.failed").value(1))
    }

    /** A retry with nothing waiting is a call that worked and found nothing to do. */
    @Test
    fun `answers an empty run when nothing is waiting`() {
        whenever(failedDownloadService.retryAll()).thenReturn(FailedDownloadRunTO())

        mvc.perform(post("/api/downloads/retry-failed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.attempted").value(0))
            .andExpect(jsonPath("$.failures").isEmpty)
    }

    @Test
    fun `retries on every call rather than once`() {
        whenever(failedDownloadService.retryAll()).thenReturn(FailedDownloadRunTO())

        mvc.perform(post("/api/downloads/retry-failed")).andExpect(status().isOk)
        mvc.perform(post("/api/downloads/retry-failed")).andExpect(status().isOk)

        // a second call is a second try, which is the whole point of a cache that keeps its failures
        verify(failedDownloadService, times(2)).retryAll()
    }
}