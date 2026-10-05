package org.endy.pmczero.model.modern

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "a.bessources")
class Bessource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

//    @Column(name = "medium_id", nullable = true)
//    var mediumId: Int? = null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medium_id", nullable = true)
    var medium: Medium? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    var created_at: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    var updated_at: LocalDateTime? = null

    @Column(name = "ress_type", nullable = true)
    var ressType: Int? = null
// RessType:
//     PRIMARY(0),
//     TN(1),
//     PIC(2),
//     URL(3),
//     FOLDER(4)

    //    @Column(name = "storage_id", nullable = true)
//    var storageId: Int? = null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_id", nullable = true)
    lateinit var storage: Storage

    @Column(name = "encrypted", nullable = true)
    var encrypted: Boolean? = false

    /**
     * The storage of this bessource, null when it has none.
     *
     * `storage` is lateinit, so reading it on a bessource that never got one throws rather than
     * returning null. This mirrors [Location.storageOrNull]: a bessource always references a storage,
     * but one built in memory (as the draft bessources of a scan are) need not have one yet, and a
     * caller has to be able to tell "no storage" from "crash".
     */
    fun storageOrNull(): Storage? = if (this::storage.isInitialized) storage else null

}

