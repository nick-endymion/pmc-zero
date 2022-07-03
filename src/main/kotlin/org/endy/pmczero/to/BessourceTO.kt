package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Storage
import java.sql.Date

data class BessourceTO (
    var id: Int? = null,
    var name: String? = null,
    var btype: Int? = null,
    var mediumId: Int? = null,
    var storageId: Int? = null,
    var encrypted: Boolean = false,
    var created_at: Date? = null,
    var updated_at: Date? = null,
)


