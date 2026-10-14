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

    /**
     * The url the full size file is served from, or null when this medium has none.
     *
     * Derived from the medium's bessources rather than stored, the same way
     * [RessourceUrlsTO.primaryUrl] is, so a caller does not have to walk bessources and
     * storage locations to find a file it was already handed the medium for.
     *
     * Null rather than empty for a medium with no primary bessource, or whose storage is
     * not in use, and also for the migrated media of type (legacy) folder, which never had
     * a file behind them. The same media are the ones [org.endy.pmczero.service.MsetService.ressourcesInUse]
     * leaves out, so a caller that skips a null here skips what that listing would have
     * left out too.
     */
    var primaryUrl: String? = null,

    /**
     * The url the thumbnail is served from, or null when there is none.
     *
     * Null for anything that is not a picture: a sound file has no thumbnail to make and a
     * request for one answers 409 rather than inventing one. As with [primaryUrl] the absence
     * is a hint about the medium rather than a broken answer, which is why the type carries
     * the media's own mtype beside it.
     */
    var tnUrl: String? = null,

    var bessources: List<BessourceTO> =  listOf()
)

