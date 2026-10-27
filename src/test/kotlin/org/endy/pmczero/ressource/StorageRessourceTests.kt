package org.endy.pmczero.ressource

import org.endy.pmczero.exception.NotAccessibleException
import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.DuplicateBessourceService
import org.endy.pmczero.service.FileInfoService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.to.BessourceTO
import org.endy.pmczero.to.FileInfoFailureTO
import org.endy.pmczero.to.FileInfoRunTO
import org.endy.pmczero.to.MediumTO
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * What `POST /api/storages/{id}/set-fileinfos` and `GET /api/storages/{id}/duplicate-bessources`
 * answer, over http.
 *
 * Only the endpoints are under test: which files are missing and how they are committed is
 * [FileInfoService] and its own tests, and a run of its own over a storage of real files would be
 * slower and would say less about this. Which bessources collide is [DuplicateBessourceService] and
 * the query under it.
 */
@WebMvcTest(controllers = [StorageRessource::class])
class StorageRessourceTests {

    @MockBean
    lateinit var storageService: StorageService

    @MockBean
    lateinit var msetService: MsetService

    @MockBean
    lateinit var fileInfoService: FileInfoService

    @MockBean
    lateinit var duplicateBessourceService: DuplicateBessourceService

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

    // -------------------------------------------------------------------------------------
    // GET /api/storages/{id}/duplicate-bessources
    // -------------------------------------------------------------------------------------

    /**
     * The media behind the collision, each with its bessources, since a medium is what the caller
     * decides about and the bessources are what says which file is in conflict.
     */
    @Test
    fun `answers the media behind the duplicate bessources with their bessources`() {
        whenever(duplicateBessourceService.duplicatesIn(1)).thenReturn(
            listOf(duplicateOf(11, "2020/a.jpg"))
        )

        mvc.perform(get("/api/storages/1/duplicate-bessources"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].id").value(11))
            .andExpect(jsonPath("$[0].name").value("2020/a.jpg"))
            .andExpect(jsonPath("$[0].bessources[0].name").value("2020/a.jpg"))
            .andExpect(jsonPath("$[0].bessources[0].locationType").value("MAIN_HTTP"))
            .andExpect(jsonPath("$[0].bessources[0].url").value("http://example.org/main/2020/a.jpg"))
    }

    /**
     * A storage nothing collides on is a successful answer and not an error: nothing has been looked
     * at that says otherwise.
     */
    @Test
    fun `answers an empty list for a storage without duplicates`() {
        whenever(duplicateBessourceService.duplicatesIn(1)).thenReturn(emptyList())

        mvc.perform(get("/api/storages/1/duplicate-bessources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    /** The storage the url names is the one asked about, since two may hold the same name. */
    @Test
    fun `asks for the storage the url names`() {
        whenever(duplicateBessourceService.duplicatesIn(7)).thenReturn(emptyList())

        mvc.perform(get("/api/storages/7/duplicate-bessources")).andExpect(status().isOk)

        verify(duplicateBessourceService).duplicatesIn(7)
    }

    @Test
    fun `answers 404 for a storage that does not exist when asking for its duplicates`() {
        whenever(duplicateBessourceService.duplicatesIn(99))
            .thenAnswer { throw NotFoundException() }

        mvc.perform(get("/api/storages/99/duplicate-bessources")).andExpect(status().isNotFound)
    }

    /** one entry as the service hands it out, so this only says what the url does with it */
    private fun duplicateOf(mediumId: Int, name: String) = MediumTO(
        id = mediumId,
        name = name,
        bessources = listOf(
            BessourceTO(
                id = 1,
                name = name,
                ressType = 0,
                storageId = 1,
                locationType = LocationType.MAIN_HTTP,
                url = "http://example.org/main/$name"
            )
        )
    )
}