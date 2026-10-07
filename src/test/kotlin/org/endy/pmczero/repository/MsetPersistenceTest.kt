package org.endy.pmczero.repository

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    // -------------------------------------------------------------------------------------
    // Stepping through the sets in id order
    // -------------------------------------------------------------------------------------

    @Test
    fun `finds the set with the smallest id above the given one`() {
        givenSets("first", "second", "third", "fourth")

        assertEquals("second", msetRepository.findFirstAboveId(firstId())?.name)
    }

    @Test
    fun `finds the set with the largest id below the given one`() {
        givenSets("first", "second", "third", "fourth")

        assertEquals("third", msetRepository.findFirstBelowId(fourthId())?.name)
    }

    @Test
    fun `skips over ids that no set has`() {
        // a deleted set leaves a gap, and the step has to be across it rather than stuck on it
        givenSets("first", "second", "third", "fourth")
        msetRepository.deleteById(secondId())
        entityManager.flush()
        entityManager.clear()

        assertEquals("third", msetRepository.findFirstAboveId(firstId())?.name)
        assertEquals("first", msetRepository.findFirstBelowId(thirdId())?.name)
    }

    @Test
    fun `answers the neighbour across a gap of more than one id`() {
        // several sets deleted between two, so the step is not merely over a single hole
        val ids = givenSets("first", "second", "third", "fourth", "fifth")
        msetRepository.deleteById(ids[1])
        msetRepository.deleteById(ids[2])
        msetRepository.deleteById(ids[3])
        entityManager.flush()
        entityManager.clear()

        assertEquals("fifth", msetRepository.findFirstAboveId(ids[0])?.name)
        assertEquals("first", msetRepository.findFirstBelowId(ids[4])?.name)
    }

    @Test
    fun `answers the neighbour of an id that no longer names a set`() {
        // only the direction matters, so the set after a deleted one is still reachable
        val ids = givenSets("first", "second", "third")
        val (first, second, third) = ids
        msetRepository.deleteById(second)
        entityManager.flush()
        entityManager.clear()

        assertEquals("third", msetRepository.findFirstAboveId(second)?.name)
        assertEquals("first", msetRepository.findFirstBelowId(third)?.name)
        // the step from the sets that are still there lands next to the gap rather than on it
        assertEquals(third, msetRepository.findFirstAboveId(first)?.id)
        assertEquals(first, msetRepository.findFirstBelowId(third)?.id)
    }

    @Test
    fun `finds no set above the highest one`() {
        givenSets("first", "second")

        assertNull(msetRepository.findFirstAboveId(secondId()))
    }

    @Test
    fun `finds no set below the lowest one`() {
        givenSets("first", "second")

        assertNull(msetRepository.findFirstBelowId(firstId()))
    }

    @Test
    fun `finds no set on either side of an id beyond the ones that exist`() {
        givenSets("first", "second")

        assertNull(msetRepository.findFirstAboveId(secondId() + 99))
        assertNull(msetRepository.findFirstBelowId(firstId() - 99))
    }

    @Test
    fun `finds no set on either side when there are none at all`() {
        assertNull(msetRepository.findFirstAboveId(1))
        assertNull(msetRepository.findFirstBelowId(1))
    }

    /**
     * the ids of four sets persisted under [names] in that order, so a test can talk about their
     * positions rather than hardcoding ids
     *
     * Read back from the database rather than off the entities, since the identity generator decides
     * the ids and a test that assumed 1, 2, 3, 4 would pass on h2 and fail on the mysql of
     * application.properties.
     */
    private fun givenSets(vararg names: String): List<Int> =
        names.map { name ->
            entityManager.persist(Mset().apply { this.name = name })
            entityManager.flush()
            entityManager.clear()
            msetRepository.findAllByNameContaining(name).single().id!!
        }

    private fun firstId(): Int = msetRepository.findAllByNameContaining("first").single().id!!
    private fun secondId(): Int = msetRepository.findAllByNameContaining("second").single().id!!
    private fun thirdId(): Int = msetRepository.findAllByNameContaining("third").single().id!!
    private fun fourthId(): Int = msetRepository.findAllByNameContaining("fourth").single().id!!

    // -------------------------------------------------------------------------------------
    // Where a set came from
    // -------------------------------------------------------------------------------------

    @Test
    fun `keeps the location, the subpath and the url of a set`() {
        givenLocation("C:\\bilder", LocationType.MAIN_FS)
        entityManager.persist(
            Mset().apply {
                name = "august 2020"
                locationId = 1
                subpath = "2020/august"
                url = "http://example.org/galerie.html"
            }
        )
        entityManager.flush()
        entityManager.clear()

        val stored = msetRepository.findAll().single()
        assertEquals(1, stored.locationId)
        assertEquals("2020/august", stored.subpath)
        assertEquals("http://example.org/galerie.html", stored.url)
    }

    @Test
    fun `keeps a set with none of them, they are all optional`() {
        // a set built by hand or migrated from a legacy folder knows none of the three
        entityManager.persist(Mset().apply { name = "empty" })
        entityManager.flush()
        entityManager.clear()

        val stored = msetRepository.findAll().single()
        assertNull(stored.locationId)
        assertNull(stored.subpath)
        assertNull(stored.url)
    }

    @Test
    fun `deleting the location of a set leaves the set alone`() {
        // Mset.locationId is a plain id, not a relation with a cascade, so a location can go while a
        // set still names it. The set is not deleted along with it
        givenLocation("C:\\bilder", LocationType.MAIN_FS)
        entityManager.persist(Mset().apply { name = "august 2020"; locationId = 1 })
        entityManager.flush()
        entityManager.clear()

        entityManager.remove(entityManager.find(Location::class.java, 1))
        entityManager.flush()

        assertEquals(1, msetRepository.count())
        assertEquals(1, msetRepository.findAll().single().locationId)
    }

    // -------------------------------------------------------------------------------------
    // Msets of a storage, once a medium of theirs is marked deleted
    // -------------------------------------------------------------------------------------

    @Test
    fun `still finds an mset whose only medium is marked deleted`() {
        // the mark is about the medium, not the set, so the set still exists on that storage
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        mediaRepository.findAll().single().deleted = true
        entityManager.flush()
        entityManager.clear()

        assertEquals(listOf("scanned"), msetRepository.findByStorageId(storage.id!!).map { it.name })
    }

    // -------------------------------------------------------------------------------------
    // Known files of the file listing
    // -------------------------------------------------------------------------------------

    @Test
    fun `reports the file of a stored medium as already existing`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)

        assertEquals(
            listOf("doc-1"),
            bessourceRepository.findNamesOfExistingMedia(
                storage.id!!, RessType.PRIMARY.i, listOf("doc-1")
            )
        )
    }

    @Test
    fun `does not report the file of a medium marked deleted`() {
        // this is what lets a scan of the same location pick the file up again
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        mediaRepository.findAll().single().deleted = true
        entityManager.flush()
        entityManager.clear()

        assertTrue(
            bessourceRepository
                .findNamesOfExistingMedia(storage.id!!, RessType.PRIMARY.i, listOf("doc-1"))
                .isEmpty()
        )
    }

    @Test
    fun `reports the file again once the mark is cleared`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        mediaRepository.findAll().single().deleted = false
        entityManager.flush()
        entityManager.clear()

        assertEquals(
            listOf("doc-1"),
            bessourceRepository.findNamesOfExistingMedia(
                storage.id!!, RessType.PRIMARY.i, listOf("doc-1")
            )
        )
    }

    @Test
    fun `treats a medium whose flag was never set as not deleted`() {
        // the column is nullable, so the rows migrated before it existed have no value at all
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        // native, because a bulk JPQL update would leave the persistence context holding the
        // old value and the test would pass for the wrong reason
        entityManager.entityManager.createNativeQuery("update a_media set deleted = null")
            .executeUpdate()
        entityManager.flush()
        entityManager.clear()

        assertEquals(
            listOf("doc-1"),
            bessourceRepository.findNamesOfExistingMedia(
                storage.id!!, RessType.PRIMARY.i, listOf("doc-1")
            )
        )
    }

    @Test
    fun `reports no file of a medium whose bessource is not primary`() {
        val storage = givenStorage("shelf")
        val medium = Medium().apply { name = "doc.pdf" }
        medium.bessources = mutableListOf(
            Bessource().apply {
                name = "doc.pdf"
                ressType = RessType.TN.i
                this.storage = storage
                this.medium = medium
            }
        )
        persistSet("scanned", listOf(medium))

        assertTrue(
            bessourceRepository
                .findNamesOfExistingMedia(storage.id!!, RessType.PRIMARY.i, listOf("doc.pdf"))
                .isEmpty()
        )
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
    // Setting the deleted flag
    // -------------------------------------------------------------------------------------

    @Test
    fun `the deleted flag is stored and read back`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        val id = mediaRepository.findAll().single().id!!

        mediaRepository.findById(id).get().deleted = true
        entityManager.flush()
        entityManager.clear()

        assertEquals(true, mediaRepository.findById(id).get().deleted)
    }

    @Test
    fun `a medium that was never marked is not deleted`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)

        // the column is nullable and the rows that predate it have no value, so a medium nobody
        // touched must not read as marked
        assertEquals(false, mediaRepository.findAll().single().deleted)
    }

    @Test
    fun `marking a medium does not delete its row or its bessource`() {
        val storage = givenStorage("shelf")
        givenSet("scanned", storage)
        val id = mediaRepository.findAll().single().id!!

        mediaRepository.findById(id).get().deleted = true
        entityManager.flush()
        entityManager.clear()

        assertEquals(1, mediaRepository.count())
        assertEquals(1, bessourceRepository.count())
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a persisted storage, so a bessource has something to point at */
    private fun givenStorage(name: String): Storage =
        Storage().apply { this.name = name }
            .also { entityManager.persist(it); entityManager.flush() }

    /**
     * a persisted FS location, so an mset can point at a real `a_locations` row rather than at an id
     * that names nothing
     */
    private fun givenLocation(uri: String, locationType: LocationType): Location =
        Location().apply {
            this.uri = uri
            this.locationType = locationType.i
            inuse = 1
            this.storage = Storage().apply { name = "storage" }
        }.also {
            // a location cannot be persisted without its storage, it holds a reference to one
            entityManager.persist(it.storage)
            entityManager.persist(it)
            entityManager.flush()
        }

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