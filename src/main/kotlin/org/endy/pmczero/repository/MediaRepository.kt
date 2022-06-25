package org.endy.pmczero.repository


import org.endy.pmczero.model.LegacyMedium
import org.endy.pmczero.model.legacy.MfilesEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MediaRepository : CrudRepository<LegacyMedium, Int> {


//    @Query("select m from LegacyMedium m join fetch m.mfiles")
//    fun getWithMfiles() : List<MfilesEntity>

}

