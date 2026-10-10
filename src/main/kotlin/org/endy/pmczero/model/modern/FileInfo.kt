package org.endy.pmczero.model.modern

import java.time.LocalDateTime
import javax.persistence.*

/**
 * What the file behind a [Bessource] is like on disk, for the bessources whose file has been looked at.
 *
 * A row is a fact about a file at a point in time, not a promise: the same file may be recorded again
 * after it has changed, and the row is replaced. A bessource without one has not been looked at, which
 * is the normal case, since nothing records these on the way in and a scan of a whole location would
 * otherwise hash every file it walks past.
 *
 * One to one with the bessource and recorded as a plain id, the same way
 * [Mset.mediumId] and [Mset.locationId] are: it says which bessource a row is about without making
 * that bessource the owner of it, so a bessource that is deleted takes its file facts with it by the
 * column rather than by a cascade nobody has to configure. The column is unique, so a bessource cannot
 * have two of these whatever writes them.
 *
 * The table is `a.file_info`, beside the other `a.` tables of this application.
 */
@Entity
@Table(name = "a.file_info")
class FileInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    /**
     * The bessource this row is about, i.e. `a.bessources.id`, and unique.
     *
     * Plain rather than a relation, as above, and named for the same reason every other column here is:
     * the database is what this application agrees with.
     *
     * Unique rather than merely indexed, since that column is what makes this one to one, and unique in
     * a way a writer has to know about: a fresh row for a bessource that already has one is refused by
     * the database rather than replacing what was there. Replacing a row is a matter of reading it
     * first and writing the same one back, which is what
     * [org.endy.pmczero.service.FileInfoService.recordFor] does.
     */
    @Column(name = "bessource_id", nullable = false, unique = true)
    var bessourceId: Int? = null

    /**
     * How large the file is, in bytes.
     *
     * A [Long] rather than an int, since the files of a video archive are larger than an int holds and
     * the column would otherwise be a limit on what may be scanned rather than a fact about what was.
     */
    @Column(name = "size", nullable = true)
    var size: Long? = null

    /**
     * When the file was created on disk.
     *
     * The file's own time, not the time this row was written, and named apart from the row's own
     * timestamps for that reason: a file copied from a backup keeps the time it was made, which is the
     * one worth knowing, while the time it was scanned into this application says when the scan ran.
     *
     * Null on a file system that does not keep it. Windows does, and so does ext4; some others report
     * the same time as the last change instead of nothing, which is a property of the file system
     * rather than a mistake here.
     */
    @Column(name = "file_created_at", nullable = true)
    var fileCreatedAt: LocalDateTime? = null

    /**
     * When the file was last changed on disk.
     *
     * The file's own time again. This is the one worth comparing against a scan: a file whose
     * [fileChangedAt] is later than the scan that recorded it has been touched since, whatever its size
     * and hash still say.
     */
    @Column(name = "file_changed_at", nullable = true)
    var fileChangedAt: LocalDateTime? = null

    /**
     * The SHA-256 of the content, in lower case hex.
     *
     * SHA-256 rather than MD5, which is what a lot of image libraries reach for, because this is a
     * content identity: two files with the same hash are the same file, and a hash that collisions can
     * be produced for is no use for that.
     *
     * The algorithm is named here rather than in a column of its own, since nothing in this application
     * picks it: a hash computed by another program is only comparable if the program is known, so a
     * caller hashing a file to check it against this one has to use SHA-256 as well.
     */
    @Column(name = "hash", nullable = true)
    var hash: String? = null
}
