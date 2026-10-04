package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Storage
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface StorageRepository : CrudRepository<Storage, Int> {

    @Query("select distinct s from Storage s left join fetch s.locations where s.id = :id ")
    fun findByIdOrNullWithLocations(id: Int): Storage?

}
