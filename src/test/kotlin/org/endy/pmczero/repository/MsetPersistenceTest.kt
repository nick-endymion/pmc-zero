package org.endy.pmczero.repository

import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import javax.persistence.PersistenceException

/**
 * What the database itself makes of an mset: what a DELETE on it leaves behind, and which msets it
 * reports for a storage. Neither the mapping nor a mocked repository answers these, only the schema
 * and the query behind them do.
 *
 * Runs on its own in memory h2, so it does not need the mysql of application.properties.
 */
@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:mset;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class MsetPersistenceTest {

    @Autowired
    lateinit var msetRepository: MsetRepository

    @Autowired
    lateinit var mediaRepository: MediaRepository

    @Autowired
    lateinit var bessourceRepository: BessourceRepository

    @Autowired
    lateinit var bookmarkRepository: BookmarkRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Test
    fun `deleting an mset deletes its media`() {
        val msetId = givenSetWithOneMedium()

        msetRepository.delete(msetRepository.findById(msetId).get())
        entityManager.flush()

        assertEquals(0, mediaRepository.count())
    }

    @Test
    fun `deleting an mset deletes the bessources of its media`() {
        val msetId = givenSetWithOneMedium()

        msetRepository.delete(msetRepository.findById(msetId).get())
        entityManager.flush()

        // Medium.bessources is mapped with CascadeType.ALL, so a bessource cannot outlive its medium
        assertEquals(0, bessourceRepository.count())
    }

    @Test
    fun `deleting an mset deletes the storage of nothing, the storage stays`() {
        val msetId = givenSetWithOneMedium()

        msetRepository.delete(msetRepository.findById(msetId).get())
        entityManager.flush()

        // a bessource references a storage, but the storage outlives its bessources
        assertEquals(1, storageRepositoryCount())
    }

    @Test
    fun `deleting a medium alone deletes its bessources too`() {
        val msetId = givenSetWithOneMedium()
        val mediumId = mediaRepository.findAll().single().id

        mediaRepository.deleteById(mediumId!!)
        entityManager.flush()

        assertEquals(0, bessourceRepository.count())
        // the set itself is untouched, only the medium was deleted
        assertEquals(1, msetRepository.count())
        assertEquals(msetId, msetRepository.findAll().single().id!!)
    }

    @Test
    fun `deleting an mset without bessources leaves the storage alone`() {
        val msetId = givenSetWithOneMedium(withBessource = false)

        msetRepository.delete(msetRepository.findById(msetId).get())
        entityManager.flush()

        assertEquals(1, storageRepositoryCount())
    }

    @Test
    fun `deleting an mset with a bookmarked medium still fails, bookmarks are not cascaded`() {
        // still open: Medium.bookmarks carries no cascade, so a bookmark outlives its medium and
        // the foreign key a_bookmark.medium_id refuses the delete. A bookmark is meant to survive
        // its medium, so this one wants an explicit decision rather than a cascade.
        val msetId = givenSetWithOneMedium(withBessource = false, withBookmark = true)

        assertThrows<PersistenceException> {
            msetRepository.delete(msetRepository.findById(msetId).get())
            entityManager.flush()
        }
    }

    // -------------------------------------------------------------------------------------
    // Msets of a storage
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the mset whose medium points at the storage`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)

        assertEquals(listOf("scanned"), msetRepository.findByStorageId(storage.id!!).map { it.name })
    }

    @Test
    fun `finds an mset once, however many of its media sit on that storage`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage, mediumCount = 3)

        // Without the distinct in the query this would come back three times.
        assertEquals(1, msetRepository.findByStorageId(storage.id!!).size)
    }

    @Test
    fun `finds an mset whose media sit on two storages`() {
        val shelf = givenStorage("shelf")
        val vault = givenStorage("vault")
        // a scanned set can well cover files of more than one storage
        givenSet("scanned", shelf, vault, mediumCount = 2)

        assertEquals(listOf("scanned"), msetRepository.findByStorageId(shelf.id!!).map { it.name })
        assertEquals(listOf("scanned"), msetRepository.findByStorageId(vault.id!!).map { it.name })
    }

    @Test
    fun `finds no mset for another storage`() {
        val shelf = givenStorage("shelf")
        givenStorage("vault")
        givenSet("scanned", shelf)

        assertTrue(msetRepository.findByStorageId(2).isEmpty())
    }

    @Test
    fun `finds no mset without media, since nothing says where its files are`() {
        val storage = givenStorage("shelf")
        entityManager.persist(Mset().apply { name = "empty" })
        entityManager.flush()

        assertTrue(msetRepository.findByStorageId(storage.id!!).isEmpty())
    }

    @Test
    fun `finds no mset whose media have no bessource`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage, withBessource = false)

        assertTrue(msetRepository.findByStorageId(storage.id!!).isEmpty())
    }

    @Test
    fun `finds the mset regardless of the type of the bessource`() {
        // a thumbnail lives in the tn location of the same storage, so it points there too
        val storage = givenStorage("shelf")
        val medium = Medium().apply { name = "doc.pdf" }
        medium.bessources = mutableListOf(
            Bessource().apply {
                name = "tn/doc.jpg"
                ressType = RessType.TN.i
                this.storage = storage
                this.medium = medium
            }
        )
        persistSet("scanned", listOf(medium))

        assertEquals(listOf("scanned"), msetRepository.findByStorageId(storage.id!!).map { it.name })
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a persisted storage, so a bessource has something to point at */
    private fun givenStorage(name: String): Storage =
        Storage().apply { this.name = name }
            .also { entityManager.persist(it); entityManager.flush() }

    /**
     * a persisted mset named [name] with [mediumCount] media, each with one primary bessource on
     * [storage], or on every storage given
     */
    private fun givenSet(
        name: String,
        vararg storage: Storage,
        mediumCount: Int = 1,
        withBessource: Boolean = true
    ) {
        val media = (1..mediumCount).map { index ->
            Medium().apply { this.name = "doc-$index" }.also { medium ->
                if (withBessource) {
                    medium.bessources = mutableListOf(
                        Bessource().apply {
                            this.name = "doc-$index"
                            ressType = RessType.PRIMARY.i
                            // every medium gets one bessource per storage, so a set really can mix
                            this.storage = storage[index % storage.size]
                            this.medium = medium
                        }
                    )
                }
            }
        }
        persistSet(name, media)
    }

    private fun persistSet(name: String, media: List<Medium>) {
        val mset = Mset().apply {
            this.name = name
            this.media = media.toMutableList()
        }
        media.forEach { it.mset = mset }
        entityManager.persist(mset)
        entityManager.flush()
        entityManager.clear()
    }

    /**
     * a persisted mset with one medium and, unless [withBessource] is off, one primary bessource on
     * it, so both variants of a medium are covered
     */
    private fun givenSetWithOneMedium(
        withBessource: Boolean = true,
        withBookmark: Boolean = false
    ): Int {
        val storage = Storage().apply { name = "storage" }
        val medium = Medium().apply { name = "doc.pdf" }
        if (withBessource) {
            val bessource = Bessource().apply {
                name = "doc.pdf"
                ressType = RessType.PRIMARY.i
                this.storage = storage
                this.medium = medium
            }
            medium.bessources = mutableListOf(bessource)
        }
        val mset = Mset().apply {
            name = "set"
            this.media = mutableListOf(medium)
            medium.mset = this
        }
        entityManager.persist(storage)
        entityManager.persist(mset)
        entityManager.flush()
        // the bookmark comes after the medium is saved, Medium.bookmarks has no cascade that would
        // save it along with the medium, and only a saved medium can be referred to
        if (withBookmark) {
            val bookmark = Bookmark().apply {
                name = "doc"
                url = "http://example.org/doc"
                this.medium = medium
            }
            medium.bookmarks = mutableListOf(bookmark)
            entityManager.persist(bookmark)
        }
        entityManager.flush()
        // clear so the delete is answered by the database and not by the persistence context
        entityManager.clear()
        return mset.id!!
    }

    private fun storageRepositoryCount(): Long =
        entityManager.entityManager.createQuery("select count(s) from Storage s", Long::class.javaObjectType)
            .singleResult
}