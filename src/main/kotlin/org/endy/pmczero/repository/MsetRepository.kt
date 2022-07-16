package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Mset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MsetRepository : CrudRepository<Mset, Int> {


    @Query("select mset from Mset mset join fetch mset.media where mset.id = :id ")
    fun findByIdOrNullWithMedia(id: Int) : Mset?

    fun findAllByNameContaining(searchTerm: String) : List<Mset>

}

