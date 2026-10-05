package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Mset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface MsetRepository : CrudRepository<Mset, Int> {


    @Query("select mset from Mset mset join fetch mset.media where mset.id = :id ")
    fun findByIdOrNullWithMedia(id: Int) : Mset?

    fun findAllByNameContaining(searchTerm: String) : List<Mset>

    /**
     * the msets that hold at least one medium with a bessource in [storageId]
     *
     * An mset carries no storage of its own, so the only way from a set to a storage runs through
     * its media and their bessources: an mset belongs to the storage its files live on. A set with
     * no media, or whose media have no bessource yet, is not found, since nothing about it says
     * where its files are. `distinct` is what keeps a set with several media in the same storage
     * from being reported once per medium.
     */
    @Query(
        "select distinct mset from Mset mset " +
            "join mset.media medium join medium.bessources bessource " +
            "where bessource.storage.id = :storageId"
    )
    fun findByStorageId(@Param("storageId") storageId: Int): List<Mset>

}

