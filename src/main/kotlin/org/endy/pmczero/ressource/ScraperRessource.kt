package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.scraper.Scraper
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.SetService
import org.endy.pmczero.to.BsetTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController("/api/scrape")
class ScraperRessource(
    val scraper: Scraper,
    val setService: SetService,
    private val mediaService: MediaService
) {

    @GetMapping("/")
    fun adf(@RequestParam(required = true) url: String): BsetTO {
        val sc = scraper.scan(url)
        return sc.set?.toTO() ?: throw  Exception()
    }

    @GetMapping("/location/{id}")  // TODO should be post
    fun ear(
        @PathVariable id: Int,
        @RequestParam(required = true) url: String
    ): String {
        scraper.scan(url)
        return url
    }

//
//    @GetMapping("/set/{id}/images/html")
//    fun getImagesHtmlPage(@PathVariable id: Int): String {
//        return setService.htmlImagePage(id, RessType.PRIMARY)
//    }
//
//    @GetMapping("/set/{id}/tns/html")
//    fun getTnsHtmlPage(@PathVariable id: Int): String {
//        return setService.htmlImagePage(id, RessType.TN)
//    }
}