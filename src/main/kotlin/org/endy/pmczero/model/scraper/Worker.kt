package org.endy.pmczero.model.scraper

import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext


interface  Worker {

    fun applya(element: String, scanningKontext: ScanningKontext)

}
