package org.endy.pmczero.repository


import org.endy.pmczero.model.LegacyMedium
import org.endy.pmczero.model.legacy.MfilesEntity
import org.endy.pmczero.model.modern.Medium
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface MediaRepository : CrudRepository<Medium, Int> {

    /**
     * the media of the set with [msetId] that carry the deletion mark, in id order
     *
     * The ones a whole set deletion has to walk before it deletes the set itself: deleting a set
     * cascades to its media and their bessources, and once that has happened the names behind those
     * bessources are gone, so the files of a marked medium could no longer be resolved and moved.
     * See [org.endy.pmczero.service.MediaDeletionService.deleteMarkedMediaOfMset].
     *
     * `deleted = true` rather than a `is not null`, since a medium nobody marked is not one to act
     * on: the column is nullable and a row migrated before it existed carries no value at all, which
     * is what [org.endy.pmczero.service.MediaDeletionService.deleteMarkedMedium] treats as unmarked
     * as well.
     *
     * Only media of this set, so a marked medium of another set is not dragged into this one, and
     * ordered by id so two calls that see the same rows move their files in the same order.
     */
    @Query(
        "select medium from Medium medium " +
            "where medium.mset.id = :msetId and medium.deleted = true " +
            "order by medium.id"
    )
    fun findAllMarkedOfMset(@Param("msetId") msetId: Int): List<Medium>

}
