package org.endy.pmczero.model

data class FolderTO (
    var id: Int? = null,
    var storageId: Int? = null,
    var mpath: String? = null,
    var lfolder: String? = null,
    var mfiles: List<MfileTO> = mutableListOf(),
    var title: String? = null
)
