package org.endy.pmczero.to

import org.endy.pmczero.model.FoldersEntity
import java.sql.Date

data class MfileTO (

    var id: Int? = null,
    val folder: FoldersEntity? = null,
    var filename: String? = null,
    var modDate: Date? = null,
    var mtype: Int? = null

)
