package org.endy.pmczero.to

data class StorageTO (
    var id: Int? = null,
    var name: String? = null,
    var no: Int? = null,
    var filepath: String? = null,
    var webpath: String? = null,
    var filepathTn: String? = null,
    var webpathTn: String? = null,
    var mtype: Int? = null,
    var fitId: Int? = null,
//    var locations: List<LocationsEntity> = mutableListOf(),
)
