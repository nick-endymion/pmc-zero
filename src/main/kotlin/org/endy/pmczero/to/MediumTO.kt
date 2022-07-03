package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Bessource
import java.sql.Date

data class MediumTO (
    var id: Int? = null,
    var name: String? = null,
    var setId: Int? = null,
    var created_at: Date? = null,
    var updated_at: Date? = null,
    var mtype: Int? = null,
    var bessources: List<BessourceTO> =  listOf()
)

