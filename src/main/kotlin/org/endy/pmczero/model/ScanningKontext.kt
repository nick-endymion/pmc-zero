package org.endy.pmczero.model

import org.endy.pmczero.model.modern.BSet
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Downloader

data class ScanningKontext(
    var location: Location,
//    var storage: Storage,
    var set: BSet?,
    var attributes: MutableList<String>,
    val downloader: Downloader
)
