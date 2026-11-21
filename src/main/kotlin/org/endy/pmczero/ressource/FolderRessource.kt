package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.map
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.to.FolderTO
import org.endy.pmczero.model.FoldersEntity
import org.endy.pmczero.repository.FolderRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
class FolderRessource(val folderRepository: FolderRepository) {

    @Deprecated("Not used by the PMC-UI frontend")
    @GetMapping("/folders")
    fun getFolders(): Iterable<FoldersEntity> {
        return folderRepository.findAll()
    }

    @Deprecated("Not used by the PMC-UI frontend")
    @GetMapping("/to/folders")
    fun getFoldersTo(): List<FolderTO> {
        return folderRepository.findAll().map { f -> f.toTO() }
    }

    @Deprecated("Not used by the PMC-UI frontend")
    @GetMapping("/folders/{id}")
    fun getFolder(@PathVariable id: Int): FoldersEntity? {
        return folderRepository.findByIdOrNull(id)
    }

    @Deprecated("Not used by the PMC-UI frontend")
    @GetMapping("/folders/{id}/to")
    fun getFolderTo(@PathVariable id: Int): FolderTO {
//        var fx = folderRepository.findByIdOrNull(id) ?: throw Exception()
        val fx = folderRepository.getWithMfiles(id) ?: throw Exception()
        return fx.toTO(true)
    }

    @Deprecated("Not used by the PMC-UI frontend")
    @PostMapping(
        path = ["/folders"], consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun setFolder(@RequestBody folderTo: FolderTO) {
        folderRepository.save(folderTo.map())
    }

}
