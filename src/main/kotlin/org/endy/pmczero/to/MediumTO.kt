package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Mset
import java.time.LocalDateTime

data class MediumTO (
    var id: Int? = null,
    var name: String? = null,
    var setId: Int? = null,
    var mset: MsetTO? = null,
    var created_at: LocalDateTime? = null,
    var updated_at: LocalDateTime? = null,
    var mtype: Int? = null,
    /** Where the medium stands in the order of its set, see [Medium.ranking]. */
    var ranking: Int? = null,
    /** Whether the medium is marked as deleted, see [Medium.deleted]. */
    var deleted: Boolean? = false,
    var bessources: List<BessourceTO> =  listOf()
)

