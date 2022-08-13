package org.endy.pmczero.ressource

import org.endy.pmczero.service.ScraperService
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.MsetService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/scrape")
class ScraperRessource(
    val scraperService: ScraperService,
    val msetService: MsetService,
    private val mediaService: MediaService
) {

//    @GetMapping("/")
//    fun adf(@RequestParam(required = true) url: String): MsetTO {
//        val sc = scraper.scan(url)
//        return sc.mset?.toTO() ?: throw  Exception()
//    }
//
//    @GetMapping("/location/{id}")  // TODO should be post
//    fun ear(
//        @PathVariable id: Int,
//        @RequestParam(required = true) url: String
//    ): String {
//        scraper.scan(url)
//        return url
//    }
//
////
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
