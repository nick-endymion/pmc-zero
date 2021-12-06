package org.endy.pmczero.ressource

import org.endy.pmczero.model.MfilesEntity
import org.endy.pmczero.repository.MfilesRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class Mfiles(val mfilesRepository: MfilesRepository) {

    @GetMapping("/mfiles")
    fun getFolders() : Iterable<MfilesEntity> {
        return mfilesRepository.findAll()
    }

    @GetMapping("/mfiles/{id}")
    fun getFolder(@PathVariable id : Int) : MfilesEntity? {
        return mfilesRepository.findByIdOrNull(id)
    }

}