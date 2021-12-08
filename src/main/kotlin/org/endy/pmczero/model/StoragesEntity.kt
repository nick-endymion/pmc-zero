package org.endy.pmczero.model

import javax.persistence.*

@Entity
@Table(name = "storages")
class StoragesEntity {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    
    @Column(name = "name", nullable = true)
    var name: String? = null

    
    @Column(name = "no", nullable = true)
    var no: Int? = null

    
    @Column(name = "filepath", nullable = true)
    var filepath: String? = null

    
    @Column(name = "webpath", nullable = true)
    var webpath: String? = null

    
    @Column(name = "filepath_tn", nullable = true)
    var filepathTn: String? = null

    
    @Column(name = "webpath_tn", nullable = true)
    var webpathTn: String? = null

    
    @Column(name = "mtype", nullable = true)
    var mtype: Int? = null

    
    @Column(name = "fit_id", nullable = true)
    var fitId: Int? = null

    @OneToMany(mappedBy = "storage",fetch = FetchType.LAZY)
    var locations: List<LocationsEntity> =  mutableListOf()

//    @Transient
//    var locationsEntity: LocationsEntity {
//        return locationsEntity
//    }

    override fun toString(): String =
        "Entity of type: ${javaClass.name} ( " +
                "id = $id " +
                "name = $name " +
                "no = $no " +
                "filepath = $filepath " +
                "webpath = $webpath " +
                "filepathTn = $filepathTn " +
                "webpathTn = $webpathTn " +
                "mtype = $mtype " +
                "fitId = $fitId " +
                ")"

    // constant value returned to avoid entity inequality to itself before and after it's update/merge
    override fun hashCode(): Int = 42

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoragesEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (no != other.no) return false
        if (filepath != other.filepath) return false
        if (webpath != other.webpath) return false
        if (filepathTn != other.filepathTn) return false
        if (webpathTn != other.webpathTn) return false
        if (mtype != other.mtype) return false
        if (fitId != other.fitId) return false

        return true
    }

}

