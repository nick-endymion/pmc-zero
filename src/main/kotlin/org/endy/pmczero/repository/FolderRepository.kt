package org.endy.pmczero.repository



import org.endy.pmczero.model.FoldersEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface FolderRepository : CrudRepository<FoldersEntity, Int> {


    @Query("select f from FoldersEntity f where f.id = :id")
    fun getWithMfiles(@Param("id") id: Int) : FoldersEntity?

}

