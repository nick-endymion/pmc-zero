package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toFatTO
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.BookmarkService
import org.endy.pmczero.to.BookmarkTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/bookmarks")
class BookmarkRessource(
    val bookmarkService: BookmarkService
) {

    @GetMapping("/{id}")
    fun getBookmark(@PathVariable id: Int): BookmarkTO {
        return bookmarkService.findById(id).toFatTO()
    }

    @GetMapping("/")
    fun getBookmarks(@RequestParam searchTerm: String?, @RequestParam url: String?): List<BookmarkTO> {
        if (searchTerm != null) {
            return bookmarkService.search(searchTerm).map { it.toTO() }
        }
        if (url != null) {
            return bookmarkService.searchByUrl(url).map { it.toTO() }
        }
        throw Exception()
    }

    @PostMapping("/")
    fun createBookmark(@RequestBody bookmarkTO: BookmarkTO): BookmarkTO {
        if (bookmarkTO.id != null) throw Exception()
        return bookmarkService.save(bookmarkTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveBookmark(@PathVariable id: Int, @RequestBody bookmarkTO: BookmarkTO): BookmarkTO {
        if (id != bookmarkTO.id) throw Exception()
        bookmarkService.save(bookmarkTO.toEntity())

        return bookmarkService.findById(id).toTO()
//        return bookmarkService.save(bookmarkTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteBookmark(@PathVariable id: Int) {
        return bookmarkService.delete(id)
    }

}
