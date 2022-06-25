package org.endy.pmczero.model

import com.fasterxml.jackson.annotation.JsonBackReference
import org.endy.pmczero.model.legacy.MfilesEntity
import javax.persistence.*

@Entity
@Table(name = "folders")
 class
FoldersEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
//    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id: Int? = null

//    @Column(name = "storage_id", nullable = true)
//    var storageId: Int? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_id")
    var storage: StoragesEntity? = null

    @Column(name = "mpath", nullable = true)
    var mpath: String = ""

    @Column(name = "lfolder", nullable = true)
    var lfolder: String = ""

    @JsonBackReference
    @OneToMany(mappedBy = "folder",fetch = FetchType.LAZY)
    var mfiles: List<MfilesEntity> =  mutableListOf()

    
    @Column(name = "title", nullable = true)
    var title: String? = null

    constructor()
    constructor(
        id: Int?,
        storage: StoragesEntity?,
        mpath: String,
        lfolder: String,
        mfiles: List<MfilesEntity>,
        title: String?,
    ) {
        this.id = id
        this.storage = storage
        this.mpath = mpath
        this.lfolder = lfolder
        this.mfiles = mfiles
        this.title = title
    }


    // constant value returned to avoid entity inequality to itself before and after it's update/merge
    override fun hashCode(): Int = 42
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FoldersEntity

        if (mfiles != other.mfiles) return false

        return true
    }

    override fun toString(): String {
        return "FoldersEntity(mfiles=$mfiles)"
    }


}

