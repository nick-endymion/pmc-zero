package org.endy.pmczero.model.scraper

import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.service.getNameInUrl

class MediaAdder : Worker {

    override fun applya(element: String, sc: ScanningKontext) {
        check(sc.set != null)
        var medium = Medium().also {
            it.name = element.getNameInUrl()
            it.mtype = Mtype.IMEDIUM.i
        }
        var b = Bessource().also {
            it.name = sc.location.getRightPart(element)
            it.btype = 0
            it.storage = sc.location.storage
        }
        medium.bessources.add(b)
        sc.set!!.media.add(medium)
    }

}
