package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.FileInfo
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface FileInfoRepository : CrudRepository<FileInfo, Int> {

    /** the file facts of [bessourceId], null when that bessource has not been looked at */
    fun findByBessourceId(bessourceId: Int): FileInfo?

    /**
     * the bessources ids of the storage [storageId] that already have a row
     *
     * One question for the whole storage rather than one per file, since a caller filling the table
     * over a storage of any size asks it once: what is missing can only be answered by subtracting
     * this from every bessource of the storage, and doing that with a query per file would be a
     * hundred thousand queries to learn the same thing once.
     *
     * The subquery rather than a join, since [FileInfo] records a bessource by id rather than by a
     * relation, so there is nothing to join on.
     */
    @Query(
        "select f.bessourceId from FileInfo f " +
            "where f.bessourceId in (select b.id from Bessource b where b.storage.id = :storageId)"
    )
    fun findBessourceIdsOfStorage(@Param("storageId") storageId: Int): List<Int>

    /**
     * Every row whose hash is one that more than one row has, together with the bessource it is about.
     *
     * One query for the whole answer, so there is no list of hashes to hand back in a second one: a
     * storage with fifty thousand duplicate files would be fifty thousand placeholders in an `in`, and
     * that is a limit rather than a detail. The duplicates are named by the subquery and read in one
     * pass over them.
     *
     * `hash is not null` inside the subquery, which is the one thing this query has to be careful
     * about: a group by counts every null of a column as one group, so a storage where a hundred
     * bessources were never hashed would otherwise answer a hundred of them as one duplicate set.
     *
     * The join to [org.endy.pmczero.model.modern.Bessource] is what a caller needs to find the files on
     * disk: a bessource name means nothing without the location it sits in, and reading
     * [org.endy.pmczero.model.modern.Bessource.storage] for that would be a lazy load per row.
     *
     * Ordered by hash and then by bessource id, so the answer is in one order whatever the database
     * feels like handing rows out in.
     */
    @Query(
        "select f.hash as hash, " +
            "f.bessourceId as bessourceId, " +
            "b.name as name, " +
            "b.storage.id as storageId, " +
            "f.size as size, " +
            "f.fileCreatedAt as fileCreatedAt, " +
            "f.fileChangedAt as fileChangedAt " +
            "from FileInfo f join Bessource b on b.id = f.bessourceId " +
            "where f.hash in (" +
            "select g.hash from FileInfo g where g.hash is not null group by g.hash having count(g) > 1" +
            ") " +
            "order by f.hash, f.bessourceId"
    )
    fun findDuplicates(): List<DuplicateFile>
}

/**
 * One row of [FileInfoRepository.findDuplicates], i.e. one file row together with the bessource it
 * belongs to.
 *
 * A projection rather than a [FileInfo] and a bessource fetched after it, since the answer is a table
 * of these rather than a list of rows to walk: naming the seven columns keeps one query and touches
 * no lazy relation.
 *
 * Everything nullable that the columns allow: a bessource may have no name and no storage, and a hash
 * is null for a row that has none.
 */
interface DuplicateFile {

    /** the content hash, which is what says these rows are the same file */
    val hash: String?

    /** the bessource this row is about */
    val bessourceId: Int?

    /** the name of that bessource, i.e. its path below the location of its storage */
    val name: String?

    /** the storage the name is relative to, without which the name says nothing on its own */
    val storageId: Int?

    /** how large the file is, which the hash already says */
    val size: Long?

    /** when the file was created on disk, which is a fact about this file rather than about the content */
    val fileCreatedAt: java.time.LocalDateTime?

    /** when the file was last changed on disk, likewise this file's own time */
    val fileChangedAt: java.time.LocalDateTime?
}