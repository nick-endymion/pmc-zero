package org.endy.pmczero.to

import org.endy.pmczero.model.modern.Bessource
import java.sql.Date

data class RessourceUrlsTO(
    var id: Int? = null,
    var name: String? = null,
    var primaryUrl: String? = null,
    var tnUrl: String? = null,

    /**
     * What kind of medium this is, the [org.endy.pmczero.model.Mtype] integer, or null for a row written
     * before the column said anything.
     *
     * Carried here because this listing is what the set's own page is drawn from, and a caller cannot tell a
     * sound file from a picture by looking at a url: both are bytes served from the same place, and the only
     * thing that says which is the extension inside [name]. A client deciding what to draw with the row it was
     * handed should not have to re-derive the type by parsing a filename.
     *
     * [tnUrl] is null for anything that is not a picture, a sound file having no thumbnail to make and a
     * request for one answering 409. That absence is a hint about the type rather than a fact about the
     * medium: one whose file has gone is missing its url for a different reason entirely.
     */
    var mtype: Int? = null,

    /**
     * Where this medium stands in the order of its set, see [org.endy.pmczero.model.modern.Medium.ranking],
     * or null for a medium nobody has ranked.
     *
     * Carried here for the same reason as [mtype]: the viewer this listing draws is the only place a
     * reader can rank a medium, so it has to show the ranking that is already on the row or it would
     * ask somebody to press a number without saying what the medium is ranked as today.
     */
    var ranking: Int? = null
)

