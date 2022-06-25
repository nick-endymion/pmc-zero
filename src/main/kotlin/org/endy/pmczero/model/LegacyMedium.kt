package org.endy.pmczero.model

import org.endy.pmczero.model.legacy.MfilesEntity
import java.sql.Date
import java.sql.Timestamp
import javax.persistence.*


@Entity
@Table(name = "media")
class LegacyMedium {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "name", nullable = true)
    var filename: String? = null

    @Column(name = "modified", nullable = true)
    var modified: Timestamp? = null

    @Column(name = "mod_date", nullable = true)
    var modDate: Date? = null

    @Column(name = "mtype", nullable = true)
    var mtype: Int? = null

//    @OneToMany(mappedBy = "legacyMedium",fetch = FetchType.LAZY)
//    var mfiles: List<MfilesEntity> =  mutableListOf()

}

