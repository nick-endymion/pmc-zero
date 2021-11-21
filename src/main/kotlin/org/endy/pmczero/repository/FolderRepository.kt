package org.endy.pmczero.repository



import org.endy.pmczero.model.FoldersEntity
import org.springframework.data.repository.CrudRepository

interface FolderRepository : CrudRepository<FoldersEntity, Int> {

}

