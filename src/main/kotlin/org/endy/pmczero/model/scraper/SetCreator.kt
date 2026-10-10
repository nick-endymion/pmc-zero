package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanningKontext
import org.endy.pmczero.model.modern.Mset

/**
 * Names the set the scan builds, and records a new one unless the run is onto a set that is there.
 *
 * The name comes from the element it is handed, which a scraper reads as the text of the `title`
 * element: the one name that says what the images are of, and a better one than the url.
 *
 * ### A run onto a set that is already there
 *
 * A set with an id is kept as it is rather than replaced. That is the case of
 * [ScanningKontext.msetId], which says the run belongs to a set that is there: a second page of a
 * gallery, a second supplier of the same set. Replacing it would answer a set holding only the media of
 * this run, and saving that onto the row of the set would leave the media of the earlier run behind on
 * that row while the set itself claimed to hold only the new ones.
 *
 * So the set is kept, media and all, and the workers of the run add to it. The row itself is not read
 * here: a [org.endy.pmczero.model.scraper.Worker] is handed an element and nothing else and has no way
 * of asking the database for anything. Whoever set [ScanningKontext.msetId] is the one that read it,
 * and a set on the kontext already carries what it found.
 */
@Serializable
@SerialName("setCreator")
 class SetCreator : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        val set = scanningKontext.mset ?: Mset()

        set.name = set.name?.takeIf { it.isNotBlank() } ?: element

        scanningKontext.mset = set
    }

 }
