package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.springframework.data.repository.CrudRepository

interface StorageRepository : CrudRepository<Storage, Int> {

}

