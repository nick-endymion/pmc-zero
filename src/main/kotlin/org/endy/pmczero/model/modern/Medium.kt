package org.endy.pmczero.model.modern

import org.endy.pmczero.model.legacy.MfilesEntity
import java.sql.Date
import javax.persistence.*

@Entity
@Table(name = "a.media")
class Medium {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "set_id", nullable = true)
    var setId: Int? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @Column(name = "created_at", nullable = true,columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP" )
    var created_at: Date? = null

    @Column(name = "updated_at", nullable = true,columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" )
    var updated_at: Date? = null

    @Column(name = "mtype", nullable = true)
    var mtype: Int? = null

    @OneToMany(mappedBy = "medium",fetch = FetchType.LAZY)
    var bessources: List<Bessource> =  mutableListOf()

}

