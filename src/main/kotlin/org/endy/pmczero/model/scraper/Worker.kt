package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext

@Serializable
sealed class Worker {

    abstract fun applya(element: String, scanningKontext: ScanningKontext)

}
