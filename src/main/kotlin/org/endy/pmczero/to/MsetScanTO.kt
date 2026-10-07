package org.endy.pmczero.to

/**
 * What a scan of a directory of a location produced or added, see
 * [org.endy.pmczero.service.LocationService.draftMset] and
 * [org.endy.pmczero.service.MsetService.expandMset].
 *
 * One shape for all three scan endpoints, since a scan is a scan: the media it made are in [mset] in
 * either case, and the only thing that differs is which set they went into.
 *
 * The counts are what tell a caller whether the scan did anything. [addedFiles] is how many media it
 * created and [knownFiles] how many files of the directory were stored already, so a directory that
 * has not changed reads as 0 added against the known count rather than as an empty result that could
 * be mistaken for a scan that failed.
 *
 * The id is [mset]'s own, null on the draft of a scan that has not been saved. It is not repeated as
 * a field here, since a second place to read the id from is a second thing that can disagree with it.
 */
data class MsetScanTO(
    /** how many media the scan created, 0 when every file of the directory was stored already */
    val addedFiles: Int,

    /** how many files of the directory were stored already, and so produced no medium */
    val knownFiles: Int,

    /** the directory below the location that was scanned */
    val subpath: String?,

    val mset: MsetTO
)