package org.endy.pmczero.to

import java.sql.Date

data class LocationTO(
    var id: Int? = null,
    var name: String? = null,
    var uri: String? = null,
    var description: String? = null,
    var locationType: Int? = null,
    var storageTO: StorageTO,
    var inuse: Byte? = null,
    var createdAt: Date? = null,
    var updatedAt: Date? = null,
    var mfileId: Int? = null,
    var origin: Byte? = null,
    var extension: String? = null
)
