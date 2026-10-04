package org.endy.pmczero.to

import java.time.LocalDateTime

data class MsetTO (
    var id: Int? = null,
    var name: String? = null,
    var created_at: LocalDateTime? = null,
    var updated_at: LocalDateTime? = null,
    val media: List<MediumTO>? =  mutableListOf<MediumTO>()
)
