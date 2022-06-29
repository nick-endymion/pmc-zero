package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.BSet

class SetCreator : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        var bset = BSet().also { it.name = element }
        scanningKontext.set = bset
    }

}
