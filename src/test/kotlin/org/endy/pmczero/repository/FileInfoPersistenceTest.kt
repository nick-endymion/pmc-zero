package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.FileInfo
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * What the database itself makes of a [FileInfo]: that the table is created, that the four facts about
 * a file survive a round trip, and that a bessource can have only one of them.
 *
 * The one to one is the reason this exists rather than a plain unit test. Nothing about the entity says
 * a bessource cannot have two rows; it is the unique column on `bessource_id` that says it, and only
 * the database enforces that.
 *
 * Runs on its own in memory h2, so it does not need the mysql of application.properties.
 */
@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:fileinfo;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class FileInfoPersistenceTest {

    @Autowired
    lateinit var fileInfoRepository: FileInfoRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Test
    fun `keeps the four facts about a file`() {
        val bessourceId = givenBessource()

        val created = LocalDateTime.of(2020, 8, 1, 12, 30)
        val changed = LocalDateTime.of(2021, 3, 4, 9, 15)

        fileInfoRepository.save(
            FileInfo().also {
                it.bessourceId = bessourceId
                it.size = 1_234_567L
                it.fileCreatedAt = created
                it.fileChangedAt = changed
                it.hash = "a".repeat(64)
            }
        )
        entityManager.flush()
        entityManager.clear()

        val stored = fileInfoRepository.findByBessourceId(bessourceId)!!
        assertEquals(bessourceId, stored.bessourceId)
        assertEquals(1_234_567L, stored.size)
        assertEquals(created, stored.fileCreatedAt)
        assertEquals(changed, stored.fileChangedAt)
        assertEquals("a".repeat(64), stored.hash)
    }

    /**
     * The file's own timestamps, not the row's: a file that was made years before it was scanned into
     * this application keeps the time it was made, and the column names are what keeps the two apart.
     */
    @Test
    fun `keeps a file that was created long before it was recorded`() {
        val bessourceId = givenBessource()

        val created = LocalDateTime.of(2015, 1, 1, 0, 0)
        fileInfoRepository.save(
            FileInfo().also {
                it.bessourceId = bessourceId
                it.fileCreatedAt = created
                it.fileChangedAt = LocalDateTime.now()
            }
        )
        entityManager.flush()
        entityManager.clear()

        assertEquals(created, fileInfoRepository.findByBessourceId(bessourceId)!!.fileCreatedAt)
    }

    /** Size over what an int holds, since the files of a video archive are larger than that. */
    @Test
    fun `keeps a size beyond what an int holds`() {
        val bessourceId = givenBessource()

        fileInfoRepository.save(FileInfo().also { it.bessourceId = bessourceId; it.size = 5_000_000_000L })
        entityManager.flush()
        entityManager.clear()

        assertEquals(5_000_000_000L, fileInfoRepository.findByBessourceId(bessourceId)!!.size)
    }

    /** A bessource nobody looked at has no row, rather than a row of zeroes. */
    @Test
    fun `finds no row for a bessource that has none`() {
        val bessourceId = givenBessource()

        assertEquals(null, fileInfoRepository.findByBessourceId(bessourceId))
    }

    /**
     * The one to one: a second row for the same bessource is refused by the database.
     *
     * Asserted on [RuntimeException] rather than on [javax.persistence.PersistenceException], since what
     * a unique column refuses with is the driver's own complaint wrapped by whichever layer catches it
     * first, and the refusal itself is the thing this is about.
     */
    @Test
    fun `refuses a second row for the same bessource`() {
        val bessourceId = givenBessource()

        fileInfoRepository.save(FileInfo().also { it.bessourceId = bessourceId; it.size = 1 })
        entityManager.flush()
        entityManager.clear()

        assertThrows<RuntimeException> {
            fileInfoRepository.save(FileInfo().also { it.bessourceId = bessourceId; it.size = 2 })
            entityManager.flush()
        }
    }

    /**
     * A recording again is an update, not a second row, which is what keeps the table one to one
     * without a caller having to know whether there was a row before.
     *
     * The row is read before it is written, as [org.endy.pmczero.service.FileInfoService.recordFor]
     * does: the unique column refuses a second insert rather than overwriting the first, so the update
     * is the caller's doing and the column is only the last word.
     */
    @Test
    fun `updates the row of a bessource rather than adding one`() {
        val bessourceId = givenBessource()

        val first = fileInfoRepository.save(
            FileInfo().also { it.bessourceId = bessourceId; it.size = 1; it.hash = "alt" }
        )
        entityManager.flush()
        entityManager.clear()

        fileInfoRepository.findByBessourceId(bessourceId)!!.also {
            it.size = 2
            it.hash = "neu"
        }.let { fileInfoRepository.save(it) }
        entityManager.flush()
        entityManager.clear()

        assertEquals(1, fileInfoRepository.count(), "one row for the bessource")
        assertEquals("neu", fileInfoRepository.findByBessourceId(bessourceId)!!.hash)
        assertEquals(first.id, fileInfoRepository.findByBessourceId(bessourceId)!!.id, "the same row")
    }

    /** Two bessources, two rows: this is a one to one and not a one to many. */
    @Test
    fun `keeps a row for each of two bessources`() {
        val first = givenBessource()
        val second = givenBessource()

        fileInfoRepository.save(FileInfo().also { it.bessourceId = first; it.size = 1 })
        fileInfoRepository.save(FileInfo().also { it.bessourceId = second; it.size = 2 })
        entityManager.flush()
        entityManager.clear()

        assertEquals(2, fileInfoRepository.count())
        assertEquals(1L, fileInfoRepository.findByBessourceId(first)!!.size)
        assertEquals(2L, fileInfoRepository.findByBessourceId(second)!!.size)
    }

    /** A row naming no bessource is not a fact about a file, so the column refuses null. */
    @Test
    fun `refuses a row without a bessource`() {
        assertThrows<RuntimeException> {
            fileInfoRepository.save(FileInfo().also { it.size = 1 })
            entityManager.flush()
        }
    }

    /** Every fact of a file is nullable but the bessource, since a file system may report none of them. */
    @Test
    fun `keeps a row that knows nothing but the bessource`() {
        val bessourceId = givenBessource()

        fileInfoRepository.save(FileInfo().also { it.bessourceId = bessourceId })
        entityManager.flush()
        entityManager.clear()

        val stored = fileInfoRepository.findByBessourceId(bessourceId)!!
        assertEquals(null, stored.size)
        assertEquals(null, stored.fileCreatedAt)
        assertEquals(null, stored.fileChangedAt)
        assertEquals(null, stored.hash)
    }

    /**
 * What [FileInfoRepository.findDuplicates] makes of a table, which is the one thing here that is a
 * question about SQL rather than about this application.
 */
@Test
    fun `answers the rows of every hash that more than one row has`() {
        givenBessource("2020/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2020/b.jpg", storageId = givenStorage("regal"))

        givenFileInfo(hash = "gleich", size = 10)
        givenFileInfo(hash = "gleich", size = 10)
        givenFileInfo(hash = "anders", size = 20)
        flush()

        val duplicates = fileInfoRepository.findDuplicates()

        assertEquals(listOf("gleich"), duplicates.map { it.hash }.distinct())
        assertEquals(2, duplicates.size, "only the hash that more than one row has")
    }

    /** The storage and the name are what a caller needs to find the files on disk. */
    @Test
    fun `answers the name and the storage of a duplicate`() {
        val storage = givenStorage("regal")
        val erstes = givenBessource("2020/a.jpg", storageId = storage)
        val zweites = givenBessource("2021/zwei.jpg", storageId = storage)

        givenFileInfo(hash = "gleich", bessourceId = erstes)
        givenFileInfo(hash = "gleich", bessourceId = zweites)
        flush()

        val bessources = fileInfoRepository.findDuplicates().sortedBy { it.name }

        assertEquals(listOf("2020/a.jpg", "2021/zwei.jpg"), bessources.map { it.name })
        assertEquals(listOf(storage, storage), bessources.map { it.storageId })
        assertEquals(listOf(erstes, zweites), bessources.map { it.bessourceId }.sortedBy { it })
    }

    /**
     * The one thing this query has to be careful about: a group by counts every null of a column as one
     * group, so a storage with a hundred unhashed bessources would otherwise be answered as a hundred
     * files that are alike.
     */
    @Test
    fun `answers nothing for rows that have no hash`() {
        givenBessource("2020/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2022/a.jpg", storageId = givenStorage("regal"))

        givenFileInfo(hash = null)
        givenFileInfo(hash = null)
        givenFileInfo(hash = null)
        flush()

        assertEquals(emptyList<DuplicateFile>(), fileInfoRepository.findDuplicates())
    }

    @Test
    fun `answers nothing when every hash is its own`() {
        givenBessource("2020/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/a.jpg", storageId = givenStorage("regal"))

        givenFileInfo(hash = "eins")
        givenFileInfo(hash = "zwei")
        flush()

        assertEquals(emptyList<DuplicateFile>(), fileInfoRepository.findDuplicates())
    }

    @Test
    fun `answers nothing for a table with nothing in it`() {
        assertEquals(emptyList<DuplicateFile>(), fileInfoRepository.findDuplicates())
    }

    /** In one order whatever the database feels like handing rows out in, so two runs can be diffed. */
    @Test
    fun `answers the rows in the order of their hash`() {
        givenBessource("2020/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2020/b.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/b.jpg", storageId = givenStorage("regal"))

        givenFileInfo(hash = "zwei")
        givenFileInfo(hash = "zwei")
        givenFileInfo(hash = "eins")
        givenFileInfo(hash = "eins")
        flush()

        assertEquals(
            listOf("eins", "eins", "zwei", "zwei"),
            fileInfoRepository.findDuplicates().map { it.hash }
        )
    }

    /** Two empty files have the hash of no bytes, so they are duplicates of each other. Said here so that it is a fact. */
    @Test
    fun `answers two empty files as duplicates of each other`() {
        givenBessource("2020/leer.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/leer.jpg", storageId = givenStorage("regal"))

        val leer = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        givenFileInfo(hash = leer, size = 0)
        givenFileInfo(hash = leer, size = 0)
        flush()

        assertEquals(2, fileInfoRepository.findDuplicates().size)
    }

    /** The size is the same for every member of a group, since the hash says what the bytes are. */
    @Test
    fun `answers the size of a duplicate`() {
        givenBessource("2020/a.jpg", storageId = givenStorage("regal"))
        givenBessource("2021/a.jpg", storageId = givenStorage("regal"))

        givenFileInfo(hash = "gleich", size = 1234)
        givenFileInfo(hash = "gleich", size = 1234)
        flush()

        assertEquals(listOf(1234L, 1234L), fileInfoRepository.findDuplicates().map { it.size })
    }

    /**
     * The medium of a bessource, so a caller who has found a file twice can delete one of the media
     * rather than hunt for the file again.
     */
    @Test
    fun `answers the medium of a duplicate`() {
        val storage = givenStorage("regal")
        val medium = givenMedium("kopie.jpg")
        val erstes = givenBessource("2020/a.jpg", storageId = storage, mediumId = medium)
        val zweites = givenBessource("2021/a.jpg", storageId = storage, mediumId = medium)

        givenFileInfo(hash = "gleich", bessourceId = erstes)
        givenFileInfo(hash = "gleich", bessourceId = zweites)
        flush()

        assertEquals(
            listOf(medium, medium),
            fileInfoRepository.findDuplicates().map { it.mediumId }
        )
    }

    /** A file nobody has made a medium of has none, which is what null is for. */
    @Test
    fun `answers no medium for a bessource that has none`() {
        val storage = givenStorage("regal")
        val ohneMedium = givenBessource("2020/verwaist.jpg", storageId = storage)

        givenFileInfo(hash = "gleich", bessourceId = ohneMedium)
        givenFileInfo(hash = "gleich")
        flush()

        val rows = fileInfoRepository.findDuplicates().sortedBy { it.name }

        assertEquals(listOf("2020/verwaist.jpg", "bilder/a.jpg"), rows.map { it.name })
        assertEquals(listOf(null, null), rows.map { it.mediumId })
    }

    // -------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------

    /** a stored row of [hash] and [size] for [bessourceId], or for a bessource of its own when that is null */
    private fun givenFileInfo(hash: String?, size: Long? = null, bessourceId: Int? = null) {
        entityManager.persist(
            FileInfo().also {
                it.bessourceId = bessourceId ?: givenBessource()
                it.hash = hash
                it.size = size
            }
        )
    }

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    /** a stored storage, answering the id it was given */
    private fun givenStorage(name: String): Int =
        Storage().also { it.name = name }.also { entityManager.persist(it) }
            .let { entityManager.flush(); it.id!! }

    /** a stored medium, answering the id it was given */
    private fun givenMedium(name: String): Int =
        Medium().apply { this.name = name }.also { entityManager.persist(it) }
            .let { entityManager.flush(); it.id!! }

    /**
     * A bessource as the database hands it back, answering the id it was given, so that `bessource_id`
     * names a row that is really there.
     *
     * Without an id of its own: [Bessource] generates one, and a persist of an entity that carries a
     * generated id is a persist of something the persistence context considers detached.
     *
     * What it points at is read back by id rather than handed over as an object, since this clears the
     * persistence context afterwards and a bessource holding a storage or a medium that was detached by
     * that would be a persist of something the database has never heard of.
     */
    private fun givenBessource(name: String = "bilder/a.jpg", storageId: Int? = null, mediumId: Int? = null): Int {
        val bessource = Bessource().apply {
            this.name = name
            storageId?.let { this.storage = entityManager.find(Storage::class.java, it) }
            mediumId?.let { this.medium = entityManager.find(Medium::class.java, it) }
        }
        entityManager.persist(bessource)
        entityManager.flush()
        entityManager.clear()
        return bessource.id!!
    }
}