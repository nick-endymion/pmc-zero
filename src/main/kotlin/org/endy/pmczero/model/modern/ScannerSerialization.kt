package org.endy.pmczero.model.modern

import java.sql.Date
import javax.persistence.*

@Entity
@Table(name = "a.scanner-serialization")
class ScannerSerialization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @Column(name = "regex", nullable = true)
    var regex: String? = null

    @Column(name = "example", nullable = true)
    var example: String? = null

    @Column(name = "serializtion", nullable = true)
    var serializtion: String? = null

}

