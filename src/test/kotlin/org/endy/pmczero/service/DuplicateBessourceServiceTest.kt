package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.StorageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * Unit tests for [DuplicateBessourceService.duplicatesIn].
 *
 * Which rows collide is the query and its own test over a real database, see
 * `BessourceDuplicatePersistenceTest`. What is here is what the service adds on top of it: the size of
 * each group, which is a property of the whole answer rather than of any one row, and the refusal to
 * answer for a storage that does not exist.
 */
class DuplicateBessourceServiceTest {

    private val bessourceRepository: BessourceRepository = mock()
    private val storageRepository: StorageRepository = mock()

    private lateinit var service: DuplicateBessourceService

    private val storageId = 5

    @BeforeEach
    fun setUp() {
        service = DuplicateBessourceService(bessourceRepository, StorageService(storageRepository))
    }

    // -------------------------------------------------------------------------------------
    // The size of each group
    // -------------------------------------------------------------------------------------

    /** Every entry carries how many rows share its name, so one row says how bad the collision is. */
    @Test
    fun `carries the size of the group on every member of it`() {
        givenRows(bessource("a.jpg"), bessource("a.jpg"), bessource("a.jpg"))

        assertEquals(listOf(3, 3, 3), service.duplicatesIn(storageId).map { it.duplicates })
    }

    /**
     * Two groups of two rather than one group of four, since the size is the number of rows behind one
     * name and not the number of rows the answer happens to hold.
     */
    @Test
    fun `counts each name on its own rather than the whole answer`() {
        givenRows(
            bessource("a.jpg"), bessource("a.jpg"),
            bessource("b.jpg"), bessource("b.jpg")
        )

        assertEquals(listOf(2, 2, 2, 2), service.duplicatesIn(storageId).map { it.duplicates })
    }

    /**
     * A name and another next to it in the list are not one group of four: the count is what says so,
     * and a caller reading one entry should not have to walk the rest of the list to learn it.
     */
    @Test
    fun `counts a group of three next to a group of two`() {
        givenRows(
            bessource("a.jpg"), bessource("a.jpg"), bessource("a.jpg"),
            bessource("b.jpg"), bessource("b.jpg")
        )

        assertEquals(listOf(3, 3, 3, 2, 2), service.duplicatesIn(storageId).map { it.duplicates })
    }

    /** A storage nothing collides on is an empty answer, not an error. */
    @Test
    fun `answers nothing for a storage without duplicates`() {
        givenRows()

        assertTrue(service.duplicatesIn(storageId).isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // What is carried over
    // -------------------------------------------------------------------------------------

    /** The medium is what a caller acts on, so it has to be on the answer of every entry. */
    @Test
    fun `carries the medium and the storage of every row`() {
        givenRows(
            Bessource().apply {
                id = 11; name = "a.jpg"; this.storage = storedStorage()
                medium = Medium().apply { id = 21 }
            },
            Bessource().apply {
                id = 12; name = "a.jpg"; this.storage = storedStorage()
                medium = Medium().apply { id = 22 }
            }
        )

        val duplicates = service.duplicatesIn(storageId)

        assertEquals(listOf(11, 12), duplicates.map { it.id })
        assertEquals(listOf(21, 22), duplicates.map { it.mediumId })
        assertEquals(listOf(storageId, storageId), duplicates.map { it.storageId })
        assertEquals(listOf("a.jpg", "a.jpg"), duplicates.map { it.name })
    }

    /** A row of a medium that was never written keeps its name rather than losing it on the way. */
    @Test
    fun `carries the name and the ressource type as they are`() {
        givenRows(
            Bessource().apply {
                id = 11; name = "tn/2020/a.jpg"; ressType = RessType.TN.i; this.storage = storedStorage()
                medium = Medium().apply { id = 21 }
            },
            Bessource().apply {
                id = 12; name = "tn/2020/a.jpg"; ressType = RessType.TN.i; this.storage = storedStorage()
                medium = Medium().apply { id = 22 }
            }
        )

        val duplicates = service.duplicatesIn(storageId)

        assertEquals(listOf("tn/2020/a.jpg", "tn/2020/a.jpg"), duplicates.map { it.name })
        assertEquals(listOf(RessType.TN.i, RessType.TN.i), duplicates.map { it.ressType })
    }

    /**
     * The order the query answered in is the order the caller gets, since a group is meant to sit
     * next to itself and re-sorting it here would put that to nothing.
     */
    @Test
    fun `keeps the order of the rows the query answered in`() {
        givenRows(bessource("b.jpg", id = 1), bessource("a.jpg", id = 2))

        assertEquals(listOf("b.jpg", "a.jpg"), service.duplicatesIn(storageId).map { it.name })
    }

    /** A storage with one row of every name is not a group, so the count on it is never read. */
    @Test
    fun `does not count a name the query would not have returned`() {
        givenRows(bessource("a.jpg", id = 1), bessource("b.jpg", id = 2))

        // one of each would be no collision at all, so this only checks the mapping does not crash on
        // a group size of one; the query is what keeps such a row out, see the persistence test
        assertEquals(listOf(1, 1), service.duplicatesIn(storageId).map { it.duplicates })
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
        whenever(storageRepository.findById(99)).thenReturn(java.util.Optional.empty())

        assertThrows<NotFoundException> {
            service.duplicatesIn(99)
        }
    }

    /** and it refuses it before asking the database about collisions, not after. */
    @Test
    fun `does not look for duplicates of a storage that does not exist`() {
        whenever(storageRepository.findById(99)).thenReturn(java.util.Optional.empty())

        assertThrows<NotFoundException> { service.duplicatesIn(99) }

        verifyNoInteractions(bessourceRepository)
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a storage that is there, since a report over one that is not is not what is under test */
    private fun storedStorage() = Storage().also { it.id = storageId }

    /** a bessource of [name] on the storage, behind a medium of its own */
    private fun bessource(name: String, id: Int = 1) = Bessource().apply {
        this.id = id
        this.name = name
        this.ressType = RessType.PRIMARY.i
        this.created_at = LocalDateTime.of(2020, 8, 1, 12, 0)
        this.storage = storedStorage()
        this.medium = Medium().apply { this.id = id * 10 }
    }

    /** the rows the query hands back, and a storage that is there to be asked about */
    private fun givenRows(vararg rows: Bessource) {
        whenever(storageRepository.findById(storageId)).thenReturn(java.util.Optional.of(storedStorage()))
        whenever(bessourceRepository.findDuplicatesOfStorage(storageId)).thenReturn(rows.toList())
    }
}