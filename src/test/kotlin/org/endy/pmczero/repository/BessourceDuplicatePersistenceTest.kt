package org.endy.pmczero.repository

import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource

/**
 * What the database itself makes of a name that two bessources of one storage hold, which is what
 * [BessourceRepository.findDuplicatesOfStorage] is asked about.
 *
 * Neither the mapping nor a mocked repository answers this one: the grouping, the subquery and the
 * order of the rows are all SQL, and a JPQL string that parses is not a JPQL string that means what
 * it looks like.
 *
 * Runs on its own in memory h2, so it does not need the mysql of application.properties.
 *
 * Ids are whatever h2 hands out and not 1,2,3: a rolled back transaction still spends an identity, so
 * the numbers climb across the tests of this class. Nothing here asserts on an absolute id for that
 * reason, and an id that a test needs is read back rather than written down.
 */
@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:duplicates;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class BessourceDuplicatePersistenceTest {

    @Autowired
    lateinit var bessourceRepository: BessourceRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    // -------------------------------------------------------------------------------------
    // What counts as a duplicate
    // -------------------------------------------------------------------------------------

    /** Two rows naming the same file on one storage: the whole reason this query exists. */
    @Test
    fun `reports both bessources of a name two of them hold`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)

        assertEquals(listOf("a.jpg", "a.jpg"), duplicatesOf(storage).map { it.name })
    }

    /** A name one row holds is not a duplicate of anything, however many other names exist. */
    @Test
    fun `reports nothing for a name only one bessource holds`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("b.jpg", storage)

        assertTrue(duplicatesOf(storage).isEmpty())
    }

    /**
     * The storage is part of what makes two rows a collision, so the same name on two storages is
     * two files that are merely called alike.
     */
    @Test
    fun `reports nothing for the same name on two different storages`() {
        val shelf = givenStorage("shelf")
        val cellar = givenStorage("cellar")
        givenBessource("a.jpg", shelf)
        givenBessource("a.jpg", cellar)

        assertTrue(duplicatesOf(shelf).isEmpty())
        assertTrue(duplicatesOf(cellar).isEmpty())
    }

    /**
     * Every member of a group is reported, not one of them: what a caller does about a duplicate is
     * done to a row, so a caller has to see both sides of it.
     */
    @Test
    fun `reports all three of a name three bessources hold`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)

        assertEquals(3, duplicatesOf(storage).size)
    }

    /**
     * A thumbnail and a primary of one name are not the same file, but they are two rows pointing at
     * one path on one storage and it is the caller who knows whether that is worth looking at, so
     * they are reported rather than ruled out here.
     */
    @Test
    fun `reports a name held by two bessources of different ressource types`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage, RessType.PRIMARY.i)
        givenBessource("a.jpg", storage, RessType.TN.i)

        assertEquals(2, duplicatesOf(storage).size)
        assertEquals(
            setOf(RessType.PRIMARY.i, RessType.TN.i),
            duplicatesOf(storage).map { it.ressType }.toSet()
        )
    }

    // -------------------------------------------------------------------------------------
    // What is left out
    // -------------------------------------------------------------------------------------

    /**
     * A medium marked deleted is not acted on any more, so its files are unknown to a scan again and
     * its rows are not a collision anybody is going to resolve.
     */
    @Test
    fun `reports nothing when both bessources belong to a medium marked deleted`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage, deleted = true)
        givenBessource("a.jpg", storage, deleted = true)

        assertTrue(duplicatesOf(storage).isEmpty())
    }

    /**
     * The same filter in both halves of the query, which is the part worth checking: a name held by
     * two live rows is a duplicate, and the marked row of that name is not a third member of it.
     */
    @Test
    fun `does not report a marked row of a name two live rows hold`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage, deleted = true)

        assertEquals(2, duplicatesOf(storage).size)
    }

    /**
     * A row of a medium nobody has marked is a live row: the column is nullable, so the rows written
     * before it existed have no value at all and must not read as marked.
     */
    @Test
    fun `reports a name held by two rows whose medium was never marked`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        // native, because a bulk JPQL update would leave the persistence context holding the old
        // value and the test would pass for the wrong reason
        entityManager.entityManager.createNativeQuery("update a_media set deleted = null")
            .executeUpdate()
        entityManager.flush()
        entityManager.clear()

        assertEquals(2, duplicatesOf(storage).size)
    }

    /**
     * A name nothing was written down for is not a name, and `group by` would fold every nameless
     * row of a storage into one group and report it as a collision of something that does not exist.
     */
    @Test
    fun `reports nothing for bessources without a name`() {
        val storage = givenStorage("shelf")
        givenBessource(null, storage)
        givenBessource(null, storage)
        givenBessource(null, storage)

        assertTrue(duplicatesOf(storage).isEmpty())
    }

    /**
     * A row with no medium behind it is left out, since there is nothing a caller could decide to
     * keep: the deletion acts on media, not on bessources.
     */
    @Test
    fun `reports nothing for a bessource that has no medium`() {
        val storage = givenStorage("shelf")
        givenBessourceWithoutMedium("a.jpg", storage)
        givenBessourceWithoutMedium("a.jpg", storage)

        assertTrue(duplicatesOf(storage).isEmpty())
    }

    @Test
    fun `reports nothing for a storage that has no bessources`() {
        givenStorage("shelf")

        assertTrue(duplicatesOf(givenStorage("other")).isEmpty())
    }

    @Test
    fun `reports nothing for a storage id that names no storage`() {
        givenBessource("a.jpg", givenStorage("shelf"))
        givenBessource("a.jpg", givenStorage("shelf"))

        assertTrue(bessourceRepository.findDuplicatesOfStorage(4242).isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // What comes back
    // -------------------------------------------------------------------------------------

    /** A group sits next to itself, so a caller can read the members of one without grouping again. */
    @Test
    fun `orders the duplicates by name and then by id`() {
        val storage = givenStorage("shelf")
        // written interleaved on purpose: ids that run 1,2,3,4 would say nothing about their names,
        // so an answer that grouped but did not order would still come back one piece per group
        givenBessource("b.jpg", storage)
        givenBessource("a.jpg", storage)
        givenBessource("b.jpg", storage)
        givenBessource("a.jpg", storage)

        val duplicates = duplicatesOf(storage)

        assertEquals(listOf("a.jpg", "a.jpg", "b.jpg", "b.jpg"), duplicates.map { it.name })
        // ids are not 1,2,3,4: every test of this class rolls back and the identity sequence does not,
        // so the order is checked against itself rather than against numbers this file happens to make
        assertTrue(
            duplicates.zipWithNext().all { (first, second) ->
                first.name != second.name || first.id!! < second.id!!
            },
            "the ids of one group must ascend, so the older row of a collision comes first"
        )
    }

    /** The same call twice answers the same thing, since the order is written into the query. */
    @Test
    fun `answers the same thing on a second call`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        entityManager.clear()

        assertEquals(
            duplicatesOf(storage).map { it.id },
            duplicatesOf(storage).map { it.id }
        )
    }

    /**
     * The query fetches the medium rather than leaving it lazy, because the caller maps the rows
     * outside the session that read them. Read here with the persistence context emptied, so a
     * missing fetch would fail instead of quietly succeeding on an open session.
     */
    @Test
    fun `reads the medium of a duplicate after the session is gone`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        entityManager.clear()

        val mediumIds = bessourceRepository.findDuplicatesOfStorage(storage.id!!)
            .map { it.medium?.id }

        assertEquals(2, mediumIds.size)
        assertTrue(mediumIds.none { it == null })
    }

    /** The same for the storage, which the caller maps onto the TO as well. */
    @Test
    fun `reads the storage of a duplicate after the session is gone`() {
        val storage = givenStorage("shelf")
        givenBessource("a.jpg", storage)
        givenBessource("a.jpg", storage)
        entityManager.clear()

        val storageIds = bessourceRepository.findDuplicatesOfStorage(storage.id!!)
            .map { it.storage.id }

        assertEquals(setOf(storage.id), storageIds.toSet())
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /**
     * the duplicates of [storage] as they come back out of the database
     *
     * the context is emptied first, so what the assertions read is what a caller of this endpoint
     * gets rather than what the test happened to leave in the persistence context
     */
    private fun duplicatesOf(storage: Storage): List<Bessource> {
        entityManager.clear()
        return bessourceRepository.findDuplicatesOfStorage(storage.id!!)
    }

    /** a persisted storage, so a bessource has something to point at */
    private fun givenStorage(name: String): Storage =
        Storage().apply { this.name = name }
            .also { entityManager.persist(it); entityManager.flush() }

    /**
     * a persisted bessource of [name] on [storage], behind a medium of its own so the row is one a
     * caller could act on
     */
    private fun givenBessource(
        name: String?,
        storage: Storage,
        ressType: Int = RessType.PRIMARY.i,
        deleted: Boolean = false
    ): Bessource {
        val medium = Medium().apply {
            this.name = name
            this.deleted = deleted
        }
        val bessource = Bessource().apply {
            this.name = name
            this.ressType = ressType
            this.storage = storage
            this.medium = medium
        }
        // Medium.bessources is mapped with CascadeType.ALL, so the medium takes its bessource with it
        medium.bessources = mutableListOf(bessource)

        entityManager.persist(medium)
        entityManager.flush()

        return bessource
    }

    /** a persisted bessource that has no medium behind it, which is a row a scan leaves behind */
    private fun givenBessourceWithoutMedium(name: String, storage: Storage): Bessource =
        Bessource().apply {
            this.name = name
            this.ressType = RessType.PRIMARY.i
            this.storage = storage
        }.also { entityManager.persist(it); entityManager.flush() }
}