package org.endy.pmczero.to

import org.endy.pmczero.model.LocationType
import java.time.LocalDateTime

data class BessourceTO (
    var id: Int? = null,
    var name: String? = null,
    var ressType: Int? = null,
    var mediumId: Int? = null,
    var storageId: Int? = null,
    var encrypted: Boolean? = false,
    var created_at: LocalDateTime? = null,
    var updated_at: LocalDateTime? = null,

    var locationType: LocationType? = null,
    var url: String? = null,
)


