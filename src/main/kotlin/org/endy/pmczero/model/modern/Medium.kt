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

    @OneToMany(mappedBy = "medium", fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST])
    var bessources: MutableList<Bessource> = mutableListOf()

    @OneToMany(
        mappedBy = "medium", fetch = FetchType.LAZY,
//        cascade = [CascadeType.ALL]
    )
    var bookmarks: MutableList<Bookmark> = mutableListOf()

}

