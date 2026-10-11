package org.endy.pmczero.ressource

import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.to.DuplicateBessourceTO
import org.endy.pmczero.to.FileInfoDuplicateTO
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * What `GET /api/file-infos/duplicates` answers.
 *
 * The endpoint is a call on a service and a mapping, so that is all this is about: which files are
 * alike is [FileInfoService] and its own tests.
 */
@WebMvcTest(controllers = [FileInfoRessource::class])
class FileInfoRessourceTests {

    @MockBean
    lateinit var fileInfoService: FileInfoService

    @Autowired
    lateinit var mvc: MockMvc

    /** A group as a caller reads it: a hash, the size of that content, and the files that have it. */
    @Test
    fun `answers the duplicates as one group per hash`() {
        whenever(fileInfoService.duplicates()).thenReturn(
            listOf(
                FileInfoDuplicateTO(
                    hash = "abc",
                    size = 2048,
                    bessources = listOf(
                        DuplicateBessourceTO(1, 3, "2020/a.jpg", null, null),
                        DuplicateBessourceTO(2, 4, "2021/a.jpg", null, null)
                    )
                )
            )
        )

        mvc.perform(get("/api/file-infos/duplicates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].hash").value("abc"))
            .andExpect(jsonPath("$[0].size").value(2048))
            .andExpect(jsonPath("$[0].bessources.length()").value(2))
            .andExpect(jsonPath("$[0].bessources[0].bessourceId").value(1))
            .andExpect(jsonPath("$[0].bessources[0].storageId").value(3))
            .andExpect(jsonPath("$[0].bessources[0].name").value("2020/a.jpg"))
    }

    /** An archive with no duplicates is a successful answer rather than an error. */
    @Test
    fun `answers an empty list when nothing is duplicated`() {
        whenever(fileInfoService.duplicates()).thenReturn(emptyList())

        mvc.perform(get("/api/file-infos/duplicates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    /** The timestamps of the files themselves, which say which of two copies is the older one. */
    @Test
    fun `answers the times of the files of a duplicate`() {
        whenever(fileInfoService.duplicates()).thenReturn(
            listOf(
                FileInfoDuplicateTO(
                    hash = "abc",
                    size = 1,
                    bessources = listOf(
                        DuplicateBessourceTO(
                            1, 3, "2020/a.jpg",
                            LocalDateTime.of(2020, 8, 1, 12, 0),
                            LocalDateTime.of(2021, 3, 4, 9, 15)
                        )
                    )
                )
            )
        )

        mvc.perform(get("/api/file-infos/duplicates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].bessources[0].fileCreatedAt").value("2020-08-01T12:00:00"))
            .andExpect(jsonPath("$[0].bessources[0].fileChangedAt").value("2021-03-04T09:15:00"))
    }
}