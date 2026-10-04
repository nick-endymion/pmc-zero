package org.endy.pmczero.to

import java.time.LocalDateTime

data class StorageTO(
    var id: Int? = null,
    var name: String? = null,
    var createdAt: LocalDateTime? = null,
    var updatedAt: LocalDateTime? = null,
    var locations: List<LocationTO> = listOf()
)
