package org.endy.pmczero.repository


import org.endy.pmczero.model.Medium
import org.endy.pmczero.model.MfilesEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MediaRepository : CrudRepository<Medium, Int> {


    @Query("select m from Medium m join fetch m.mfiles")
    fun getWithMfiles() : List<MfilesEntity>

}

