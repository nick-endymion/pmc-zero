package org.endy.pmczero.model

import com.fasterxml.jackson.annotation.JsonManagedReference
import java.sql.Date
import java.sql.Timestamp
import javax.persistence.*


@Entity
@Table(name = "mfiles", catalog = "")
class MfilesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    var id: Int? = null


//    @Column(name = "folder_id", nullable = true)
//    var folderId: Int? = null

    @JsonManagedReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    val folder: FoldersEntity? = null

    @Column(name = "filename", nullable = true)
    var filename: String? = null


    @Column(name = "modified", nullable = true)
    var modified: Timestamp? = null


    @Column(name = "mod_date", nullable = true)
    var modDate: Date? = null


    @Column(name = "mtype", nullable = true)
    var mtype: Int? = null


    // constant value returned to avoid entity inequality to itself before and after it's update/merge
    override fun hashCode(): Int = 42
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MfilesEntity

        if (id != other.id) return false
        if (folder != other.folder) return false
        if (filename != other.filename) return false
        if (modified != other.modified) return false
        if (modDate != other.modDate) return false
        if (mtype != other.mtype) return false

        return true
    }

    override fun toString(): String {
        return "MfilesEntity(id=$id, folder=$folder, filename=$filename, modified=$modified, modDate=$modDate, mtype=$mtype)"
    }

    constructor()
    constructor(id: Int?, filename: String?, modified: Timestamp?, modDate: Date?, mtype: Int?) {
        this.id = id
        this.filename = filename
        this.modified = modified
        this.modDate = modDate
        this.mtype = mtype
    }

    fun getURI(): String {
        return "TODO"
    }

}

