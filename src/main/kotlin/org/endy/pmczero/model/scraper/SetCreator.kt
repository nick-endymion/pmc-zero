package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Mset

@Serializable
@SerialName("setCreator")
class SetCreator : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        var bset = Mset().also { it.name = element }
        scanningKontext.set = bset
    }

}
