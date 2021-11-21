package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.map
import org.endy.pmczero.model.FolderTO
import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.repository.FolderRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
class Folder(val folderRepository: FolderRepository) {

    @GetMapping("/folders")
    fun getFolders(): Iterable<FoldersEntity> {
        return folderRepository.findAll()
    }

    @GetMapping("/folders/{id}")
    fun getFolder(@PathVariable id: Int): FoldersEntity? {
        return folderRepository.findByIdOrNull(id)
    }


    @PostMapping(
        path = ["/folders"], consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun setFolder(@RequestBody folderTo: FolderTO) {
        folderRepository.save(folderTo.map())
    }

}