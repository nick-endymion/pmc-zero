package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.RessType
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.SetService
import org.endy.pmczero.to.BookmarkTO
import org.endy.pmczero.to.BsetTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sets")
class SetRessource(
    val setService: SetService,
    private val mediaService: MediaService
) {

    @GetMapping("/{id}")
    fun getBookmark(@PathVariable id: Int): BsetTO {
        return setService.findById(id).toTO()
    }

    @GetMapping("/")
    fun getBookmarks(@RequestParam searchTerm: String): List<BsetTO> {
        return setService.search(searchTerm).map { it.toTO() }
    }

    @PostMapping("/")
    fun createBookmark(@RequestBody bsetTO: BsetTO): BsetTO {
        if (bsetTO.id != null) throw Exception()
        return setService.save(bsetTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveBookmark(@PathVariable id: Int, @RequestBody bsetTO: BsetTO): BsetTO {
        if (id != bsetTO.id) throw Exception()
        setService.save(bsetTO.toEntity())
        return setService.save(bsetTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteBookmark(@PathVariable id: Int) {
        return setService.delete(id)
    }


    @GetMapping("/set/{id}/images/html")
    fun getImagesHtmlPage(@PathVariable id: Int): String {
        return setService.htmlImagePage(id, RessType.PRIMARY)
    }

    @GetMapping("/set/{id}/tns/html")
    fun getTnsHtmlPage(@PathVariable id: Int): String {
        return setService.htmlImagePage(id, RessType.TN)
    }
}
