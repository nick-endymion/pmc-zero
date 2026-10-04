package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Medium
import java.time.LocalDateTime
import javax.persistence.*

data class BookmarkTO(
    var id: Int? = null,
    var name: String? = null,
    var url: String? = null,
    var created_at: LocalDateTime? = null,
    var updated_at: LocalDateTime? = null,
    val mediumId: Int? = null,
    var medium: MediumTO? = null
)

