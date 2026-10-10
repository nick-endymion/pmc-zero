package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.FileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

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
     * A bessource as the database hands it back, answering the id it was given, so that `bessource_id`
     * names a row that is really there.
     *
     * Without an id of its own: [Bessource] generates one, and a persist of an entity that carries a
     * generated id is a persist of something the persistence context considers detached.
     */
    private fun givenBessource(): Int {
        val bessource = Bessource().apply { name = "bilder/a.jpg" }
        entityManager.persist(bessource)
        entityManager.flush()
        entityManager.clear()
        return bessource.id!!
    }
}