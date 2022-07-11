package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.service.BookmarkService
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.SetService
import org.endy.pmczero.to.BookmarkTO
import org.springframework.web.bind.annotation.*
import java.awt.print.Book

@RestController
@RequestMapping("/api/bookmarks")
class BookmarkRessource(
    val bookmarkService: BookmarkService
) {

    @GetMapping("/{id}")
    fun getBookmark(@PathVariable id: Int): BookmarkTO {
        return bookmarkService.findById(id).toTO()
    }

    @GetMapping("/")
    fun getBookmarks(@RequestParam searchTerm: String): List<BookmarkTO> {
        return bookmarkService.search(searchTerm).map { it.toTO() }
    }

    @PostMapping("/")
    fun createBookmark(@RequestBody bookmarkTO: BookmarkTO): BookmarkTO {
        if (bookmarkTO.id != null) throw Exception()
        return bookmarkService.save(bookmarkTO.toEntity()).toTO()
    }

    @PutMapping("/{id}")
    fun saveBookmark(@PathVariable id: Int, @RequestBody bookmarkTO: BookmarkTO): BookmarkTO {
        if (id != bookmarkTO.id) throw Exception()
        return bookmarkService.save(bookmarkTO.toEntity()).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteBookmark(@PathVariable id: Int) {
        return bookmarkService.delete(id)
    }

}
