package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.to.MfileTO
import org.endy.pmczero.model.RessType
import org.endy.pmczero.repository.MfilesRepository
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.MfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView

@RestController
class BessourceRessource(
    val mfilesRepository: MfilesRepository,
    private val mediaService: MediaService
) {

//    @GetMapping("/mfiles/to")
//    fun getMfilesTO(): Iterable<MfileTO> {
////        return mfilesRepository.getWithfolder().map { m -> m.toTO() }
//        return mfilesRepository.findAll().map { m -> m.toTO() }
//    }

//    @GetMapping("/mfiles/{id}")
//    fun getMfile(@PathVariable id: Int): MfilesEntity? {
//        return mfilesRepository.findByIdOrNull(id)
//    }

    @GetMapping("/medium/{id}/url")
    fun getMfilePic(@PathVariable id: Int): String {
        return mediaService.url(id,RessType.PRIMARY)
    }

    @GetMapping("/medium/{id}/binary")
    fun showMfilePic(@PathVariable id: Int): RedirectView
    {
        return RedirectView( mediaService.url(id,RessType.PRIMARY))
    }

    @GetMapping("/medium/{id}/tn/url")
    fun afa(@PathVariable id: Int): String {
        return mediaService.url(id,RessType.TN)
    }



//    @GetMapping("/medium/{id}/image")
//    fun getMediumPic(@PathVariable id: Int): String {
//        return mediaService.urlByMediumId(id,RessType.PIC)
//    }
}
