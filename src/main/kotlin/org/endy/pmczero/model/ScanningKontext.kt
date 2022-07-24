package org.endy.pmczero.model

import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.service.Downloader

data class ScanningKontext(
    var location: Location,
//    var storage: Storage,
    var mset: Mset?,
    var attributes: MutableList<String>,
    val downloader: Downloader
)
