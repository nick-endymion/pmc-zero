package org.endy.pmczero.service

import org.endy.pmczero.repository.DuplicateFile
import org.endy.pmczero.repository.FileInfoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FileInfoService.duplicates], over the rows the query hands back.
 *
 * The query itself is tested against a real database in
 * [org.endy.pmczero.repository.FileInfoPersistenceTest]; what is here is what is done with its rows,
 * which is the grouping and nothing else.
 */
class FileInfoDuplicatesTests {

    private val fileInfoRepository: FileInfoRepository = mock()

    private lateinit var service: FileInfoService

    @BeforeEach
    fun setUp() {
        service = FileInfoService(fileInfoRepository, mock(), mock(), mock())

        // answered as the list stands, so a test may add rows after the service has been asked once
        whenever(fileInfoRepository.findDuplicates()).thenAnswer { answers.toList() }
    }

    @Test
    fun `groups the rows of one hash into one answer`() {
        given(hash = "gleich", bessourceId = 1, name = "2020/a.jpg", storageId = 3, size = 100)
        given(hash = "gleich", bessourceId = 2, name = "2021/a.jpg", storageId = 3, size = 100)

        val duplicates = service.duplicates()

        assertEquals(1, duplicates.size)
        assertEquals("gleich", duplicates.single().hash)
        assertEquals(2, duplicates.single().bessources.size)
    }

    /** Each member says enough to find its file: which bessource, which storage, which name. */
    @Test
    fun `answers the bessources of a duplicate with what finds their files`() {
        val created = LocalDateTime.of(2020, 8, 1, 12, 0)
        given(hash = "gleich", bessourceId = 7, name = "2020/a.jpg", storageId = 3, fileCreatedAt = created)

        val member = service.duplicates().single().bessources.single()

        assertEquals(7, member.bessourceId)
        assertEquals(3, member.storageId)
        assertEquals("2020/a.jpg", member.name)
        assertEquals(created, member.fileCreatedAt)
    }

    /**
     * What a caller acts on rather than on the file: the medium is what
     * [org.endy.pmczero.service.MediaDeletionService] deletes, and a file that has no medium is one
     * nobody has made a medium of yet.
     */
    @Test
    fun `answers the medium of a duplicate`() {
        given(hash = "gleich", bessourceId = 7, mediumId = 42)
        given(hash = "gleich", bessourceId = 8, mediumId = null)

        val members = service.duplicates().single().bessources

        assertEquals(listOf(42, null), members.map { it.mediumId })
    }

    /** The size of the content, which is the same for every member and is what a caller reads first. */
    @Test
    fun `answers the size of a duplicate once`() {
        given(hash = "gleich", bessourceId = 1, size = 4096)
        given(hash = "gleich", bessourceId = 2, size = 4096)

        assertEquals(4096L, service.duplicates().single().size)
    }

    /** In the order the rows came in, which the query orders by hash. */
    @Test
    fun `answers one group per hash`() {
        given(hash = "eins", bessourceId = 1)
        given(hash = "eins", bessourceId = 2)
        given(hash = "zwei", bessourceId = 3)
        given(hash = "zwei", bessourceId = 4)
        given(hash = "drei", bessourceId = 5)
        given(hash = "drei", bessourceId = 6)

        assertEquals(
            listOf("eins", "zwei", "drei"),
            service.duplicates().map { it.hash }
        )
    }

    /** In the order the rows came in, which is the order of the hash. */
    @Test
    fun `answers the groups in the order of their rows`() {
        given(hash = "zwei", bessourceId = 3)
        given(hash = "zwei", bessourceId = 4)
        given(hash = "eins", bessourceId = 1)
        given(hash = "eins", bessourceId = 2)

        assertEquals(listOf("zwei", "eins"), service.duplicates().map { it.hash })
    }

    /**
     * The query only ever hands over a hash that more than one row has, so nothing here has to count
     * again: a group of one is not a duplicate and the database is what says which groups those are.
     */
    @Test
    fun `leaves the counting to the query`() {
        given(hash = "gleich", bessourceId = 1)

        val duplicates = service.duplicates()

        assertEquals(1, duplicates.single().bessources.size)
    }

    /** Nothing to report is a successful answer rather than an error. */
    @Test
    fun `answers nothing when the table has nothing to say`() {
        assertEquals(emptyList(), service.duplicates())
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** one row of the query, i.e. a file row and the bessource it belongs to */
    private fun given(
        hash: String,
        bessourceId: Int,
        name: String? = "bilder/$bessourceId.jpg",
        storageId: Int? = 1,
        mediumId: Int? = null,
        size: Long? = null,
        fileCreatedAt: LocalDateTime? = null,
        fileChangedAt: LocalDateTime? = null
    ) {
        answers.add(
            Row(
                hash = hash,
                bessourceId = bessourceId,
                name = name,
                storageId = storageId,
                mediumId = mediumId,
                size = size,
                fileCreatedAt = fileCreatedAt,
                fileChangedAt = fileChangedAt
            )
        )
    }

    private val answers = mutableListOf<DuplicateFile>()

    /** the rows, as a copy, so a test adds to them after the service has been asked once */
    private data class Row(
        override val hash: String,
        override val bessourceId: Int,
        override val name: String?,
        override val storageId: Int?,
        override val mediumId: Int?,
        override val size: Long?,
        override val fileCreatedAt: LocalDateTime?,
        override val fileChangedAt: LocalDateTime?
    ) : DuplicateFile
}