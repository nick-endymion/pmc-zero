package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.ScannerShort
import org.endy.pmczero.model.modern.Scanner
import org.springframework.data.repository.CrudRepository

interface SerializedScannerRepository : CrudRepository<Scanner, Int> {

  fun findAllByValid(valid: Boolean) : List<ScannerShort>
  fun findAllByNameContaining(searchTerm: String) : List<Scanner>
}

