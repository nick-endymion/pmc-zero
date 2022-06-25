package org.endy.pmczero.repository


import org.endy.pmczero.model.legacy.MfilesEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MfilesRepository : CrudRepository<MfilesEntity, Int> {


    @Query("select m from MfilesEntity m join fetch m.folder")
    fun getWithfolder() : List<MfilesEntity>

}

