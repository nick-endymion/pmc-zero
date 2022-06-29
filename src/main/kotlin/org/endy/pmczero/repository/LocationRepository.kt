package org.endy.pmczero.repository


import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.modern.Location
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface LocationRepository : CrudRepository<Location, Int> {


    fun findLocationsByNameStartingWith(url: String) : List<Location>
//    @Query("select m from MfilesEntity m join fetch m.folder")
//    fun getWithfolder() : List<MfilesEntity>

}

