package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.service.getNameInUrl

@Serializable
@SerialName("MediaAdder")
class MediaAdder : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        check(scanningKontext.set != null)
        val medium = Medium().also {
            it.name = element.getNameInUrl()
            it.mtype = Mtype.IMEDIUM.i
        }
        val b = Bessource().also {
            it.name = scanningKontext.location.getRightPart(element)
            it.btype = 0
            it.storage = scanningKontext.location.storage
        }
        medium.bessources.add(b)
        scanningKontext.set!!.media.add(medium)
    }

}
