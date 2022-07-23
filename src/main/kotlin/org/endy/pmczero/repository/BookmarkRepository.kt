package org.endy.pmczero.repository


import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Bookmark
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface BookmarkRepository : CrudRepository<Bookmark, Int> {

  fun findAllByNameContaining(searchTerm: String) : List<Bookmark>
  fun findAllByUrlEquals(url: String) :List<Bookmark>
}

