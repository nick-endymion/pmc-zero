package org.endy.pmczero.model.modern

import java.sql.Date
import javax.persistence.*

@Entity
@Table(name = "a.serialized.scanner")
class Scanner {
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

    @Column(
        name = "serialization",
        columnDefinition = "TEXT",
        nullable = true
    )
    var serialization: String? = null

    @Column(name = "valid", nullable = true)
    var valid: Boolean = false

}

