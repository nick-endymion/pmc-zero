package org.endy.pmczero.to

/**
 * What expanding one mset with the files of a directory did.
 *
 * A scan is not all-or-nothing here, and the counts are what tell a caller whether it did anything:
 * [addedFiles] is how many media were created, [knownFiles] how many files of the directory were
 * stored already and therefore left alone. A call that answers 0 and the known count of the directory
 * is the honest answer for a directory that has not changed, rather than an empty result that could
 * be read as a scan that failed.
 *
 * [mset] is the set as it was saved, with every medium it holds, so a caller sees the new ones in
 * place without a second request. [subpath] is the directory that was scanned, which is the set's own
 * [org.endy.pmczero.model.modern.Mset.subpath] unless the caller asked for a different one.
 */
data class MsetExpansionTO(
    val msetId: Int,

    /** how many media were created, 0 when the directory held nothing new */
    val addedFiles: Int,

    /** how many files of the directory were stored already, and so produced no medium */
    val knownFiles: Int,

    /** the directory below the location that was scanned */
    val subpath: String?,

    val mset: MsetTO
)