package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Bookmark
import org.springframework.data.repository.CrudRepository

interface SetRepository : CrudRepository<BSet, Int> {

    fun findAllByNameContaining(searchTerm: String) : List<BSet>

}

