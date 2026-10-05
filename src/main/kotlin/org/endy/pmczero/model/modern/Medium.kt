package org.endy.pmczero.model.modern

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "a.media")
class Medium {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id", nullable = true)
    var mset: Mset? = null
//    @Column(name = "set_id", nullable = true)
//    var setId: Int? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    var created_at: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    var updated_at: LocalDateTime? = null

    @Column(name = "mtype", nullable = true)
    var mtype: Int? = null

    /**
     * Whether this medium is marked as deleted, so the row stays but is not acted on any more.
     *
     * A mark rather than a deletion, which is why the row survives: the media of a scanned set can
     * be large, and the decision what to do with them is not always the one that was made when they
     * were found. Nothing removes the row on its own, see
     * [org.endy.pmczero.service.MediaService.setDeleted] for what does set the flag.
     *
     * A marked medium does not count as known in the file listing any more, so a scan of the same
     * location lists its files again and can create them anew. That is what makes the flag a way to
     * bring media back.
     */
    @Column(name = "deleted", nullable = true)
    var deleted: Boolean? = false

    /**
     * The bessources of this medium, deleted with it.
     *
     * ALL rather than PERSIST alone, so a bessource cannot outlive its medium: a_bessources.medium_id
     * is a foreign key onto a_media and the database refuses to delete a medium that bessources
     * still point at. A bessource is meaningless without the medium it belongs to, so nothing is
     * lost by removing it along with the medium, and nothing that has to be kept is deleted with
     * it.
     */
    @OneToMany(mappedBy = "medium", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    var bessources: MutableList<Bessource> = mutableListOf()

    @OneToMany(
        mappedBy = "medium", fetch = FetchType.LAZY,
//        cascade = [CascadeType.ALL]
    )
    var bookmarks: MutableList<Bookmark> = mutableListOf()

}

