package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.SerializedScanner
import org.springframework.data.repository.CrudRepository

interface SerializedScannerRepository : CrudRepository<SerializedScanner, Int> {

  fun findAllByNameContaining(searchTerm: String) : List<SerializedScanner>
}

