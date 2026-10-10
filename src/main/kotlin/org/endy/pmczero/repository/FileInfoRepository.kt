package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.FileInfo
import org.springframework.data.repository.CrudRepository

interface FileInfoRepository : CrudRepository<FileInfo, Int> {

    /** the file facts of [bessourceId], null when that bessource has not been looked at */
    fun findByBessourceId(bessourceId: Int): FileInfo?
}
