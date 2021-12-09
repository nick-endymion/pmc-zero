package org.endy.pmczero.to

import org.endy.pmczero.to.MfileTO
import org.endy.pmczero.to.StorageTO

data class FolderTO (
    var id: Int? = null,
    var storage: StorageTO? = null,
    var mpath: String = "",
    var lfolder: String = "",
    var mfiles: List<MfileTO> = mutableListOf(),
    var title: String? = null
)
