package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.BSet
import org.springframework.data.repository.CrudRepository

interface SetRepository : CrudRepository<BSet, Int> {


}

