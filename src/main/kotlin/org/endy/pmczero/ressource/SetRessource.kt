package org.endy.pmczero.ressource

import org.endy.pmczero.model.RessType
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.SetService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController("/api")
class SetRessource(
    val setService: SetService,
    private val mediaService: MediaService
) {

    @GetMapping("/set/{id}/images/html")
    fun getImagesHtmlPage(@PathVariable id: Int): String {
        return setService.htmlImagePage(id, RessType.PRIMARY)
    }

    @GetMapping("/set/{id}/tns/html")
    fun getTnsHtmlPage(@PathVariable id: Int): String {
        return setService.htmlImagePage(id, RessType.TN)
    }
}
