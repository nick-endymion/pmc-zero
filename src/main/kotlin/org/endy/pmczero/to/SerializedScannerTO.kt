package org.endy.pmczero.to


data class SerializedScannerTO (
    var id: Int? = null,
    var name: String? = null,
    var regex: String? = null,
    var example: String? = null,
    var serialization: String? = null,
    var valid: Boolean = false
)
