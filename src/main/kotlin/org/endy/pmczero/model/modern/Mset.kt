package org.endy.pmczero.model.modern

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "a.sets")
class Mset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "medium_id", nullable = true)
    var mediumId: Int? = null

    /**
     * The location this set was scanned into, i.e. `a_locations.id`, or null when none is known.
     *
     * A plain id rather than a relation, the same way [mediumId] is: it records where a set came from
     * without making the location the owner of it. Nothing here cascades, so a location can be deleted
     * while sets still point at its id, which is the point of recording it rather than holding it.
     *
     * Optional because a set is not always the result of a scan of a known location: a set built by
     * hand, or one that came in with the legacy folders, has none. It is also not a replacement for
     * the way to a storage through the media, see
     * [org.endy.pmczero.repository.MsetRepository.findByStorageId], which still walks
     * set -> media -> bessource -> storage and so also finds sets whose files sit on a second
     * storage than the location they were scanned into.
     */
    @Column(name = "location_id", nullable = true)
    var locationId: Int? = null

    /**
     * The directory below [locationId] this set was scanned out of, `/` separated and without a
     * leading or trailing slash, e.g. `2020/august`. Null when there is none, which is the case for
     * the location root itself and for a set that did not come from a scan of a location.
     *
     * The same form as the `subpath` a scan is asked for and as
     * [org.endy.pmczero.model.ScanningKontext.locationPath], so the directory a set was built from can
     * be handed back to another call without translating it. It is what tells two sets apart that a
     * location scan split by top level folder, since those share a name derived from the folder while
     * living in different places.
     *
     * Kept as text rather than derived from the media: the directory a set was scanned out of is a
     * fact about the scan, and it survives the files being moved or deleted afterwards. Together with
     * [locationId] it says where the set is, and [Location.uri] is the root the two are relative to.
     */
    @Column(name = "subpath", nullable = true)
    var subpath: String? = null

    /**
     * The page this set was imported from, e.g. `http://example.org/galerie.html`, or null when the
     * set did not come from a page.
     *
     * Only meaningful for an imported set. A location scan has no page behind it, which is why this is
     * null rather than the uri of the location: the two answer different questions, and the location
     * already has a uri of its own in [locationId].
     */
    @Column(name = "url", nullable = true)
    var url: String? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    var created_at: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    var updated_at: LocalDateTime? = null

    @OneToMany(mappedBy = "mset",fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    var media: MutableList<Medium> =  mutableListOf()

}

