package org.endy.pmczero.model.modern

import java.io.File
import java.sql.Date
import javax.persistence.*

@Entity
@Table(name = "a.locations")
class Location {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @Column(name = "uri", nullable = true)
    var uri: String? = null

    @Column(name = "description", nullable = true)
    var description: String? = null

    @Column(name = "location_typ", nullable = true)
    var locationType: Int? = null
    // 1  http main
    // 2  file main
    // 3  http tn
    // 4 file tn

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_id")
    lateinit var storage: Storage

    @Column(name = "inuse", nullable = true)
    var inuse: Byte? = null

    @Column(name = "created_at", nullable = true)
    var createdAt: Date? = null

    @Column(name = "updated_at", nullable = true)
    var updatedAt: Date? = null

//    @Column(name = "prefix", nullable = true)
//    var prefix: String? = null
//
    @Column(name = "mfile_id", nullable = true)
    var mfileId: Int? = null

    @Column(name = "origin", nullable = true)
    var origin: Byte? = null

    @Column(name = "filetype", nullable = true)
    var extension: String? = null

    override fun toString(): String =
        "Entity of type: ${javaClass.name} ( " +
                "id = $id " +
                "name = $name " +
                "uri = $uri " +
                "description = $description " +
                "typ = $locationType " +
                "inuse = $inuse " +
                "createdAt = $createdAt " +
                "updatedAt = $updatedAt " +
                "mfileId = $mfileId " +
                "origin = $origin " +
                ")"

    // constant value returned to avoid entity inequality to itself before and after it's update/merge
    override fun hashCode(): Int = 42

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Location

        if (id != other.id) return false
        if (name != other.name) return false
        if (uri != other.uri) return false
        if (description != other.description) return false
        if (locationType != other.locationType) return false
        if (inuse != other.inuse) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
//        if (prefix != other.prefix) return false
        if (mfileId != other.mfileId) return false
        if (origin != other.origin) return false

        return true
    }

    fun withFolderPath(folder: String) : String{
        return uri?: "" + File.separator + folder
    }

    fun getRightPart(elementUri: String) : String {
        val length = uri?.length ?: throw Exception()
        if (uri != elementUri.substring(0, length))
            throw Exception()  //todo
        return elementUri.substring(length)
    }

}

