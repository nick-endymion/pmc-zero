package org.endy.pmczero.to

import java.util.*

data class ScanningResultTO (
    val mset: MsetTO,
    val commonUrlStart: String,
    val locations:  List<LocationTO> =  mutableListOf<LocationTO>()
)
