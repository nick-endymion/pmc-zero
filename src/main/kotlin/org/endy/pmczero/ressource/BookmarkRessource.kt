package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.service.BookmarkService
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.SetService
import org.endy.pmczero.to.BookmarkTO
import org.springframework.web.bind.annotation.*
import java.awt.print.Book

@RestController
@RequestMapping("/api/bookmark")
class BookmarkRessource(
    val bookmarkService: BookmarkService
) {

    @GetMapping("/{id}")
    fun getBookmark(@PathVariable id: Int): Bookmark {
        return bookmarkService.findById(id)
    }

    @PostMapping("/")
    fun createBookmark(@RequestBody bookmarkTO: BookmarkTO): Bookmark {
        if (bookmarkTO.id != null) throw Exception()
        return bookmarkService.save(bookmarkTO.toEntity())
    }

    @PutMapping("/{id}")
    fun saveBookmark(@PathVariable id: Int, @RequestBody bookmarkTO: BookmarkTO): Bookmark {
        if (id != bookmarkTO.id) throw Exception()
        return bookmarkService.save(bookmarkTO.toEntity())
    }
}
