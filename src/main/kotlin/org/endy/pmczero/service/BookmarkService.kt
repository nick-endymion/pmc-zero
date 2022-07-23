package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.BookmarkRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.to.BookmarkTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val mediaService: MediaService,
) {

    fun findById(id: Int): Bookmark {
        return bookmarkRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun search(searchTerm: String): List<Bookmark> {
        return bookmarkRepository.findAllByNameContaining(searchTerm)
    }
    fun searchByUrl(url: String): List<Bookmark> {
        return bookmarkRepository.findAllByUrlEquals(url)
    }


    @Transactional
    fun delete(id: Int) {
        val bookmark =  findById(id)
        bookmarkRepository.delete(bookmark)
        if (bookmark.medium !=null && bookmark.medium?.id != null)
            mediaService.delete(bookmark.medium!!.id!!)
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
        mediaService.save(bookmark.medium!!)
        return bookmarkRepository.save((bookmark))
    }


}
