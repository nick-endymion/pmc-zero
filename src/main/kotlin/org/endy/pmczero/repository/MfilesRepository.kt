package org.endy.pmczero.repository



import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.model.MfilesEntity
import org.springframework.data.repository.CrudRepository

interface MfilesRepository : CrudRepository<MfilesEntity, Int> {

}

