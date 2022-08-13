package org.endy.pmczero.to

data class SourceToScanTO(
    var url: String?,
    var bookmarkId: Int?,
    var scannerId: Int,
    var persist: Boolean?,
    var locationId: Int?
)

