package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.FileInfo
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface FileInfoRepository : CrudRepository<FileInfo, Int> {

    /** the file facts of [bessourceId], null when that bessource has not been looked at */
    fun findByBessourceId(bessourceId: Int): FileInfo?

    /**
     * the ids of the bessources on [storageId] that already have a row
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
}