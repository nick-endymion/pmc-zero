package org.endy.pmczero.to

import java.time.LocalDateTime

/**
 * One bessource whose name is held by at least one other bessource on the same storage, see
 * `DuplicateBessourceService.duplicatesIn`.
 *
 * The other half of what [FileInfoDuplicateTO] reports, and deliberately not the same thing: that one
 * groups by content hash over the whole database and answers "which files are the same bytes", this
 * one groups by name within one storage and answers "which two rows point at one file". The same two
 * bessources land in both lists whenever the files really are the same file, and in neither when they
 * are not: a copy of a picture under a second name is the same bytes and not a name collision, and a
 * second row naming a file that has since been replaced underneath is a collision and not the same
 * bytes. A caller after either asks for its own url.
 *
 * One entry per bessource rather than per name, since a bessource is what a caller decides something
 * about: the row behind the medium that stays against the row that does not. So the whole colliding
 * group is in the answer, once per member, and [name] together with [storageId] is what says which
 * entries belong together.
 *
 * [duplicates] is on every entry rather than only once per group, so a caller reading a single row
 * does not have to have seen the rest of the list to know how bad the collision is. It counts the
 * members of the group, the entry itself included, so a name held by three rows comes back three
 * times with a `duplicates` of 3, and a name that is alone never comes back at all.
 *
 * The ressource type is passed on rather than grouped by, so a primary and a thumbnail of one name on
 * the same storage are reported as well: they are not the same file, and it is the caller who knows
 * whether that matters here.
 */
data class DuplicateNameBessourceTO(
    val id: Int?,
    val name: String?,
    val storageId: Int?,
    val ressType: Int?,
    val mediumId: Int?,

    /** when this bessource was recorded, which says which of the colliding rows is the older one */
    val created_at: LocalDateTime?,

    /** how many bessources share this name on this storage, this one included */
    val duplicates: Int
)