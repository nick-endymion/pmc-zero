package org.endy.pmczero.to

/**
 * A content hash that more than one file has, and the bessources whose files those are.
 *
 * What a caller does with this is decide which of the files to keep and which to remove, so a group
 * rather than a flat list: the members of one are interchangeable by definition, and what is to be
 * acted on is the set.
 *
 * [size] is the size of that content and is therefore the same for every member, since the hash says
 * what the bytes are. It is here because it is the number a caller wants to see next to a hash: two
 * files of three megabytes are worth a look and two of four hundred bytes often are not.
 *
 * Two files of the same content that are empty count as duplicates of each other, since the hash of
 * no bytes is the hash of no bytes. A caller that does not want those filters on [size].
 */
data class FileInfoDuplicateTO(
    val hash: String,

    /** how large that content is, in bytes, and the same for every member of the group */
    val size: Long?,

    /** the bessources whose file has this hash, ordered by id */
    val bessources: List<DuplicateBessourceTO>
)

/**
 * One of the bessources of a [FileInfoDuplicateTO], as far as a caller can find the file with and act
 * on it.
 *
 * The name alone says nothing: it is a path below the location of a storage, so [storageId] is what
 * makes it addressable, and [bessourceId] is what this application knows the file by.
 *
 * [mediumId] is what a caller acts on rather than on either of them: deleting a file means deleting the
 * medium that points at it, and [org.endy.pmczero.service.MediaDeletionService] is what does that
 * properly, i.e. marks the medium, moves the file below `DELETED` and leaves the file where it was
 * rather than erasing it. A bessource of a file nobody has made a medium of has none, which is what
 * null is for.
 */
data class DuplicateBessourceTO(
    val bessourceId: Int,
    val storageId: Int?,

    /** the medium that points at this file, i.e. what is deleted to get rid of the copy */
    val mediumId: Int?,

    /** the path of the file below the location of that storage */
    val name: String?,

    /** when this file was created on disk, which says which of the two is the older copy */
    val fileCreatedAt: java.time.LocalDateTime?,

    /** when this file was last changed on disk */
    val fileChangedAt: java.time.LocalDateTime?
)