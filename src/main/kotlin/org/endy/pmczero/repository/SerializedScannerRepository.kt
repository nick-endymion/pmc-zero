package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.ScannerShort
import org.endy.pmczero.model.modern.SerializedScanner
import org.springframework.data.repository.CrudRepository

interface SerializedScannerRepository : CrudRepository<SerializedScanner, Int> {

  fun findAllByValid(valid: Boolean) : List<ScannerShort>
  fun findAllByNameContaining(searchTerm: String) : List<SerializedScanner>
}

