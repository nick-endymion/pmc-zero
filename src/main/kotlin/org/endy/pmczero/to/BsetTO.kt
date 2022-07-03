package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.to.MfileTO
import org.endy.pmczero.to.StorageTO
import java.sql.Date
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.FetchType
import javax.persistence.OneToMany

data class BsetTO (
    var id: Int? = null,
    var name: String? = null,
    var created_at: Date? = null,
    var updated_at: Date? = null,
    var media: List<MediumTO> =  listOf()
)
