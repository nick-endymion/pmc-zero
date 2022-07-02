package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.BSet

@Serializable
@SerialName("setCreator")
class SetCreator : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        var bset = BSet().also { it.name = element }
        scanningKontext.set = bset
    }

}
