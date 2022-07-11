package org.endy.pmczero.to

import java.util.*

data class BsetTO (
    var id: Int? = null,
    var name: String? = null,
    var created_at: Date? = null,
    var updated_at: Date? = null,
    var media: List<MediumTO> =  listOf()
)
