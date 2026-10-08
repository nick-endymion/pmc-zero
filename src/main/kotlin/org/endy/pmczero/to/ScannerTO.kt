package org.endy.pmczero.to


/**
 * A stored scanner as it goes over the api.
 *
 * [regex] and [supplierIdentifcator] are two different questions about a url and are worth telling
 * apart: [regex] says which urls belong to this scanner at all, [supplierIdentifcator] says how to take
 * the id of the supplier out of one that does.
 */
data class ScannerTO (
    var id: Int? = null,
    var name: String? = null,

    /** which urls this scanner is meant for, as a regex */
    var regex: String? = null,

    /** how to take the id of the supplier out of a url, as a regex */
    var supplierIdentifcator: String? = null,

    var example: String? = null,
    var serialization: String? = null,
    var valid: Boolean = false
)
