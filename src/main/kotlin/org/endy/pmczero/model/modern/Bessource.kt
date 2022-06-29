package org.endy.pmczero.model.modern

import java.sql.Date
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
    val medium: Medium? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @Column(name = "created_at", nullable = true )
    var created_at: Date? = null

    @Column(name = "updated_at", nullable = true )
    var updated_at: Date? = null

    @Column(name = "btype", nullable = true)
    var btype: Int? = null
     // 0 > primary
     // 1 > pic presentation
     // 2 > tn presentation

//    @Column(name = "storage_id", nullable = true)
//    var storageId: Int? = null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_id", nullable = true)
    lateinit var storage: Storage

    var encrypted: Boolean = false

}

