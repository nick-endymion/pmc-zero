package org.endy.pmczero.repository

import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import javax.persistence.PersistenceException

/**
 * What a DELETE on an mset leaves behind in the database: its media, their bessources and their
 * bookmarks. The mapping alone does not answer it, the database constraint does.
 *
 * Runs on its own in memory h2, so it does not need the mysql of application.properties.
 */
@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:cascade;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class MsetDeleteCascadeTest {

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