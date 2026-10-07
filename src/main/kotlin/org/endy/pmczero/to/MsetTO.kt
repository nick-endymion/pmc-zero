package org.endy.pmczero.to

import java.time.LocalDateTime

data class MsetTO (
    var id: Int? = null,
    var name: String? = null,

    /**
     * The location this set was scanned into, or null when none is known. See
     * [org.endy.pmczero.model.modern.Mset.locationId]
     */
    var locationId: Int? = null,

    /**
     * The directory below [locationId] this set was scanned out of, or null when there is none. See
     * [org.endy.pmczero.model.modern.Mset.subpath]
     */
    var subpath: String? = null,

    /**
     * The page this set was imported from, or null when it did not come from a page. See
     * [org.endy.pmczero.model.modern.Mset.url]
     */
    var url: String? = null,

    var created_at: LocalDateTime? = null,
    var updated_at: LocalDateTime? = null,
    val media: List<MediumTO>? =  mutableListOf<MediumTO>()
)
