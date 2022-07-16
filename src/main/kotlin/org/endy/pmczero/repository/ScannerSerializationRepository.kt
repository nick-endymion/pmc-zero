package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.ScannerSerialization
import org.springframework.data.repository.CrudRepository

interface ScannerSerializationRepository : CrudRepository<ScannerSerialization, Int> {

  fun findAllByNameContaining(searchTerm: String) : List<ScannerSerialization>
}

