package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.StorageRepository
import org.endy.pmczero.to.MediumTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [DuplicateBessourceService.duplicatesIn].
 *
 * Which rows collide is the query and its own test over a real database, see
 * `BessourceDuplicatePersistenceTest`. What is here is what the service adds on top of it: one entry
 * per medium rather than per colliding row, the order of the answer, and the refusal to answer for a
 * storage that does not exist. The bessources of a medium are filled in by [MediaService.mediumTO],
 * which has its own tests.
 */
class DuplicateBessourceServiceTest {

    private val bessourceRepository: BessourceRepository = mock()
    private val storageRepository: StorageRepository = mock()
    private val mediaService: MediaService = mock()

    private lateinit var service: DuplicateBessourceService

    private val storageId = 5

    @BeforeEach
    fun setUp() {
        service = DuplicateBessourceService(
            bessourceRepository,
            StorageService(storageRepository),
            mediaService
        )
        whenever(mediaService.mediumTO(any())).thenAnswer { invocation ->
            MediumTO(id = invocation.getArgument<Medium>(0).id)
        }
    }

    // -------------------------------------------------------------------------------------
    // One entry per medium
    // -------------------------------------------------------------------------------------

    /** Two rows of one medium are one entry, since a medium is what the caller acts on. */
    @Test
    fun `answers a medium with more than one colliding row once`() {
        givenRows(
            bessource(mediumId = 21, id = 11),
            bessource(mediumId = 21, id = 12)
        )

        assertEquals(listOf(21), service.duplicatesIn(storageId).map { it.id })
        verify(mediaService, times(1)).mediumTO(any())
    }

    /** Two media behind a collision are two entries, in the order the query answered them in. */
    @Test
    fun `answers one entry per medium in the order of the colliding rows`() {
        givenRows(
            bessource(mediumId = 21, id = 11, name = "a.jpg"),
            bessource(mediumId = 22, id = 12, name = "a.jpg")
        )

        assertEquals(listOf(21, 22), service.duplicatesIn(storageId).map { it.id })
    }

    /** A medium keeps the place of its first colliding row, even when one of its rows comes later. */
    @Test
    fun `keeps the place of the first row of a medium`() {
        givenRows(
            bessource(mediumId = 21, id = 11, name = "a.jpg"),
            bessource(mediumId = 22, id = 12, name = "b.jpg"),
            bessource(mediumId = 21, id = 13, name = "c.jpg")
        )

        assertEquals(listOf(21, 22), service.duplicatesIn(storageId).map { it.id })
    }

    /** A storage nothing collides on is an empty answer, not an error. */
    @Test
    fun `answers nothing for a storage without duplicates`() {
        givenRows()

        assertTrue(service.duplicatesIn(storageId).isEmpty())
        verifyNoInteractions(mediaService)
    }

    /** A row without a medium is not a medium a caller could decide something about. */
    @Test
    fun `leaves out a colliding row without a medium`() {
        givenRows(bessource(mediumId = null, id = 11))

        assertTrue(service.duplicatesIn(storageId).isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // What is refused
    // -------------------------------------------------------------------------------------

    /**
     * A mistyped id is not a storage without duplicates, which is the one answer that would be
     * believed: the query behind this would happily answer an empty list for a storage that is not
     * there.
     */
    @Test
    fun `refuses a storage that does not exist`() {
        whenever(storageRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.duplicatesIn(99)
        }
    }

    /** and it refuses it before asking the database about collisions, not after. */
    @Test
    fun `does not look for duplicates of a storage that does not exist`() {
        whenever(storageRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.duplicatesIn(99) }

        verifyNoInteractions(bessourceRepository)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a storage that is there, since a report over one that is not is not what is under test */
    private fun storedStorage() = Storage().also { it.id = storageId }

    /** a colliding bessource of [name] on the storage, behind the medium with [mediumId] */
    private fun bessource(
        mediumId: Int?,
        id: Int = 1,
        name: String = "a.jpg"
    ) = Bessource().apply {
        this.id = id
        this.name = name
        this.ressType = RessType.PRIMARY.i
        this.storage = storedStorage()
        this.medium = mediumId?.let { Medium().apply { this.id = it } }
    }

    /** the rows the query hands back, and a storage that is there to be asked about */
    private fun givenRows(vararg rows: Bessource) {
        whenever(storageRepository.findById(storageId)).thenReturn(Optional.of(storedStorage()))
        whenever(bessourceRepository.findDuplicatesOfStorage(storageId)).thenReturn(rows.toList())
    }
}
