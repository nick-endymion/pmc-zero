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

    /**
     * How the id of a supplier is taken out of the url of one of its pages, as a regex.
     *
     * Separate from [regex], which says which urls belong to this supplier at all: that one is matched
     * against a whole url to pick the scanner, this one is matched to pull a value out of a url that
     * was already picked. A url like `https://example.org/artikel/4711/fotos` would carry
     * `"(.*)/artikel/([0-9]+)/.*"` here and yield `4711`, which is what a supplier calls the thing this
     * scanner fetches.
     *
     * Not read by anything on the server: [regex] is what [org.endy.pmczero.service.ScannerService.findByUrl]
     * matches, and this is left to whoever stores and reads the id afterwards, i.e. the frontend. So it
     * is carried through the api and nothing more, and no attempt is made here to agree on a capture
     * group index with a client.
     *
     * Nullable rather than blank-defaulted for the same reason [regex] is: a supplier whose ids are not
     * in its urls at all should say so by being empty, not by carrying a pattern that never matches.
     *
     * **The name is misspelled** on purpose, to match what was asked for. Renaming it is a one line
     * change here plus the same in [ScannerTO] and the two mappers, and a column rename in a database
     * that holds rows.
     */
    @Column(name = "supplier_identifcator", nullable = true)
    var supplierIdentifcator: String? = null

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

