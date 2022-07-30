package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.RessType
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.to.RessourceUrlsTO
import org.endy.pmczero.to.MsetTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/msets")
class MsetRessource(
    val msetService: MsetService,
    private val mediaService: MediaService
) {

    @GetMapping("/{id}")
    fun getMset(@PathVariable id: Int): MsetTO {
        return msetService.findById(id).toTO()
    }

    @GetMapping("/{id}/media")
    fun getMsetWithMedia(@PathVariable id: Int): MsetTO {
        return msetService.findById(id).toTOwithMedia()
    }

    @GetMapping("/{id}/ressources-urls")
    fun getMsetWithMediax(@PathVariable id: Int): List<RessourceUrlsTO> {
        return msetService.ressourcesInUse(id)
    }

    @GetMapping("/")
    fun getMsets(@RequestParam searchTerm: String): List<MsetTO> {
        return msetService.search(searchTerm).map { it.toTO() }
    }

    @PostMapping("/")
    fun createMset(@RequestBody msetTO: MsetTO): MsetTO {
        if (msetTO.id != null) throw Exception()
        return msetService.save(msetTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveMset(@PathVariable id: Int, @RequestBody msetTO: MsetTO): MsetTO {
        if (id != msetTO.id) throw Exception()
        msetService.save(msetTO.toEntity())
        return msetService.save(msetTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteMset(@PathVariable id: Int) {
        return msetService.delete(id)
    }


    @GetMapping("/{id}/images/html")
    fun getImagesHtmlPage(@PathVariable id: Int): String {
        return msetService.htmlImagePage(id, RessType.PRIMARY)
    }

    @GetMapping("/{id}/tns/html")
    fun getTnsHtmlPage(@PathVariable id: Int): String {
        return msetService.htmlImagePage(id, RessType.TN)
    }
}
