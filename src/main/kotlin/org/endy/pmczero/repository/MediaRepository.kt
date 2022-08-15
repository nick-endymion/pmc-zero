package org.endy.pmczero.repository


import org.endy.pmczero.model.LegacyMedium
import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.modern.Medium
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MediaRepository : CrudRepository<Medium, Int> {


}

