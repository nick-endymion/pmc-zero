package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.Mtype
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium

/**
 * Records the element it is handed as a medium of the mset of the scan.
 *
 * The bessource carries the path of the file below the location, which is what
 * [org.endy.pmczero.service.LocationService.url] builds the served url from. That path includes the
 * [ScanningKontext.locationPath] of the scan, so the files of a run are recorded below the folder they
 * were downloaded into rather than in the location root.
 *
 * Which path that is depends on the location, see [ScanPath.bessourceNameOf]: an FS location holds
 * files and the element is a remote url, so the name comes from the element, while an http location is
 * itself served out of a url and the name is the part of the element below the uri of the location.
 * Recording the same path [FileDownloader] writes to is the point, since a medium whose file is not
 * where it says is a url that answers 404.
 *
 * The medium is named after the file alone rather than after the path, without the query, so two
 * galleries that hold an `a.jpg` each are two equally named media rather than one name per folder,
 * the way [org.endy.pmczero.service.LocationService.draftMset] names the media of a scanned
 * directory. See [ScanPath.mediumNameOf].
 *
 * The type stays [Mtype.IMEDIUM] whatever the extension, which says where the medium was found rather
 * than what it is: a medium a scan ran into is an internet one, and the extension tells nothing about
 * that. Deriving it instead would relabel every medium a scan finds, which is a change of meaning for
 * the rows already stored.
 */
@Serializable
@SerialName("mediaAdder")
class MediaAdder : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        check(scanningKontext.mset != null)

        val name = ScanPath.bessourceNameOf(element, scanningKontext)

        val medium = Medium().also {
            it.name = ScanPath.mediumNameOf(name)
            it.mtype = Mtype.IMEDIUM.i
        }
        val b = Bessource().also {
            it.name = name
            it.ressType = RessType.PRIMARY.i
            it.storage = scanningKontext.location.storage
            // the bessource points back at its medium, the way a saved one would, so a caller that
            // inspects the draft before persisting it sees the same shape as a persisted one and
            // saving the mset writes the whole tree rather than orphaning the bessources
            it.medium = medium
        }
        medium.bessources.add(b)
        scanningKontext.mset!!.media.add(medium)
    }

}
