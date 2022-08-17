package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Medium
import java.sql.Date
import javax.persistence.*

data class BookmarkTO(
    var id: Int? = null,
    var name: String? = null,
    var url: String? = null,
    var created_at: java.util.Date? = null,
    var updated_at: java.util.Date? = null,
    val mediumId: Int? = null,
    var medium: MediumTO? = null
)

