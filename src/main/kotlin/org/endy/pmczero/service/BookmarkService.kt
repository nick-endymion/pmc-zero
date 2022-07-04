package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.BookmarkRepository
import org.endy.pmczero.repository.MediaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val mediaService: MediaService,
) {

    fun findById(id: Int): Bookmark {
        return bookmarkRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun save(bookmark: Bookmark): Bookmark {
        if (bookmark.medium == null) {
            val medium =
                mediaService.save(Medium().also {
                    it.name = bookmark.name
                    it.mtype = Mtype.BOOKMARK.i
                })
            bookmark.medium = medium
        }
        return bookmarkRepository.save((bookmark))
    }


}
