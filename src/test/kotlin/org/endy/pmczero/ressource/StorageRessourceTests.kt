package org.endy.pmczero.ressource

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.FileInfoFailureTO
import org.endy.pmczero.to.FileInfoRunTO
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
 * What `POST /api/storages/{id}/set-fileinfos` answers, over http.
 *
 * Only the endpoint is under test: which files are missing and how they are committed is
 * [FileInfoService] and its own tests, and a run of its own over a storage of real files would be
 * slower and would say less about this.
 */
@WebMvcTest(controllers = [StorageRessource::class])
class StorageRessourceTests {

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var msetService: MsetService

    @MockBean
    lateinit var fileInfoService: FileInfoService

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `answers what the run recorded`() {
        givenStorage(1)
        whenever(fileInfoService.recordMissingOf(1)).thenReturn(
            FileInfoRunTO(storageId = 1, attempted = 3, recorded = 2, skipped = 1, failed = 0)
        )

        mvc.perform(post("/api/storages/1/set-fileinfos"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.storageId").value(1))
            .andExpect(jsonPath("$.attempted").value(3))
            .andExpect(jsonPath("$.recorded").value(2))
            .andExpect(jsonPath("$.skipped").value(1))
            .andExpect(jsonPath("$.failed").value(0))
            .andExpect(jsonPath("$.failures").isEmpty)
    }

    /** The counts, since a run over a storage is long and the answer is what says how it went. */
    @Test
    fun `answers the failures of a run that could not read every file`() {
        givenStorage(1)
        whenever(fileInfoService.recordMissingOf(1)).thenReturn(
            FileInfoRunTO(
                storageId = 1,
                attempted = 2,
                recorded = 1,
                failed = 1,
                failures = listOf(FileInfoFailureTO(3, NotAccessibleException("no file on disk")))
            )
        )

        mvc.perform(post("/api/storages/1/set-fileinfos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.failures[0].bessourceId").value(3))
    }

    /**
     * A mistyped id is not a storage with nothing to record: the run would answer success over a
     * storage that does not exist, which is the one answer that would be believed.
     */
    @Test
    fun `answers 404 for a storage that does not exist`() {
        // thenAnswer and not thenThrow: the exceptions of this application extend Throwable, which mockito
        // will not let a stub throw since findById does not declare it
        whenever(storageService.findById(99)).thenAnswer { throw NotFoundException() }

        mvc.perform(post("/api/storages/99/set-fileinfos")).andExpect(status().isNotFound)

        verifyNoInteractions(fileInfoService)
    }

    @Test
    fun `records the storage the url names`() {
        givenStorage(7)
        whenever(fileInfoService.recordMissingOf(7)).thenReturn(FileInfoRunTO(storageId = 7))

        mvc.perform(post("/api/storages/7/set-fileinfos")).andExpect(status().isOk)

        verify(fileInfoService).recordMissingOf(7)
    }

    /** a storage that is there, since a run over one that is not is not what is under test */
    private fun givenStorage(id: Int) {
        whenever(storageService.findById(id)).thenReturn(Storage().also { it.id = id })
    }
}