package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.to.MediumTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bookmarks")
class MediumRessource(
    private val mediaService: MediaService
) {


    @GetMapping("/{id}")
    fun getBookmark(@PathVariable id: Int): MediumTO {
        return mediaService.findById(id).toTO()
    }
//    TODO
//    get with Mset (Attributes)

}
