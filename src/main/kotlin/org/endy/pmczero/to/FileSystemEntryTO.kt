package org.endy.pmczero.to

import java.time.Instant

/**
 * One entry of a directory listing, as returned by the file system listing of a location.
 *
 * [name] is the path of the entry relative to the directory that was listed, always with '/' as
 * separator. For a non recursive listing that is just the name of the entry, for a recursive one it
 * is the path below the listed directory.
 *
 * [path] is the path of the entry relative to the root of the location, always with '/' as
 * separator, so it does not depend on which directory was listed and can be handed back as the
 * subdir of a new listing. It is empty for the location root itself.
 *
 * The listing carries two entries whose [name] is the conventional '.' and '..': '.' is the listed
 * directory itself and '..' its parent, which is left out when the location itself is listed. Only
 * those two names are relative markers, [path] always points at a real folder.
 *
 * [existsAlready] tells a caller which files are known already: it is true when a medium with a
 * primary bessource pointing at this entry is stored in the database, which is exactly what
 * scanning this entry would create. A directory carries false, a directory is no medium.
 */
data class FileSystemEntryTO(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Instant?,
    val existsAlready: Boolean = false
)