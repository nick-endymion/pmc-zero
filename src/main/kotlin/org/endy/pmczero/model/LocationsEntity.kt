package org.endy.pmczero.model

import java.io.File
import javax.persistence.*

@Entity
@Table(name = "locations")
class LocationsEntity {

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

    @Column(name = "typ", nullable = true)
    var typ: Int? = null

    @ManyToOne
    @JoinColumn(name = "storage_id")
    var storage: StoragesEntity? = null

    @Column(name = "inuse", nullable = true)
    var inuse: Byte? = null

    @Column(name = "created_at", nullable = true)
    var createdAt: java.sql.Timestamp? = null

    @Column(name = "updated_at", nullable = true)
    var updatedAt: java.sql.Timestamp? = null

    @Column(name = "prefix", nullable = true)
    var prefix: String? = null

    @Column(name = "mfile_id", nullable = true)
    var mfileId: Int? = null

    @Column(name = "origin", nullable = true)
    var origin: Byte? = null

    override fun toString(): String =
        "Entity of type: ${javaClass.name} ( " +
                "id = $id " +
                "name = $name " +
                "uri = $uri " +
                "description = $description " +
                "typ = $typ " +
                "inuse = $inuse " +
                "createdAt = $createdAt " +
                "updatedAt = $updatedAt " +
                "prefix = $prefix " +
                "mfileId = $mfileId " +
                "origin = $origin " +
                ")"

    // constant value returned to avoid entity inequality to itself before and after it's update/merge
    override fun hashCode(): Int = 42

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LocationsEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (uri != other.uri) return false
        if (description != other.description) return false
        if (typ != other.typ) return false
        if (inuse != other.inuse) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (prefix != other.prefix) return false
        if (mfileId != other.mfileId) return false
        if (origin != other.origin) return false

        return true
    }

    fun withFolderPath(folder: String) : String{
        return uri?: "" + File.separator + folder;
    }

}

