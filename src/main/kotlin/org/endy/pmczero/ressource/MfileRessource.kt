package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.to.MfileTO
import org.endy.pmczero.model.RessType
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.MfileRepository
import org.endy.pmczero.service.MfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class MfileRessource(
    val mfilesRepository: MfileRepository,
    private val mfileService: MfileService
) {

//    @GetMapping("/mfiles")
//    fun getMfiles(): Iterable<MfilesEntity> {
//        return mfilesRepository.findAll()
//    }

    @GetMapping("/mfiles/to")
    fun getMfilesTO(): Iterable<MfileTO> {
//        return mfilesRepository.getWithfolder().map { m -> m.toTO() }
        return mfilesRepository.findAll().map { m -> m.toTO() }
    }

//    @GetMapping("/mfiles/{id}")
//    fun getMfile(@PathVariable id: Int): MfilesEntity? {
//        return mfilesRepository.findByIdOrNull(id)
//    }

    @GetMapping("/x/mfiles/{id}/pic")
    fun getMfilePic(@PathVariable id: Int): String {
        return mfileService.url(id,RessType.PIC)
    }

    @GetMapping("/x/medium/{id}/image")
    fun getMediumPic(@PathVariable id: Int): String {
        return mfileService.urlByMediumId(id,RessType.PIC)
    }
}
