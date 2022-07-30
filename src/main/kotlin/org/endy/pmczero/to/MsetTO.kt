package org.endy.pmczero.to

import java.util.*

data class MsetTO (
    var id: Int? = null,
    var name: String? = null,
    var created_at: Date? = null,
    var updated_at: Date? = null,
    val media: List<MediumTO>? =  mutableListOf<MediumTO>()
)
