package org.endy.pmczero.to

import java.util.*

data class ScanningResultTO (
    val msetTO: MsetTO,
    val locationList:  List<LocationTO> =  mutableListOf<LocationTO>()
)
