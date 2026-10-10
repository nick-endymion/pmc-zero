package org.endy.pmczero.repository

import org.endy.pmczero.model.modern.Mset
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface MsetRepository : CrudRepository<Mset, Int> {


    @Query("select mset from Mset mset join fetch mset.media where mset.id = :id ")
    fun findByIdOrNullWithMedia(id: Int) : Mset?

    fun findAllByNameContaining(searchTerm: String) : List<Mset>

    /**
     * the msets that were imported from exactly [url], in id order
     *
     * More than one is the normal case rather than the odd one: a gallery imported twice, once with
     * its files and once with `noDownload` to fetch them later, is two sets of the same page, and a
     * caller asking what is stored for a url wants both. A set that was not imported from a page has
     * no url at all and is not found here, see [Mset.url].
     *
     * Exact rather than a `containing`, since a url is a whole value: a search on a fragment would
     * answer the sets of every page that mentions this one, which is a different question and one
     * [findAllByNameContaining] already asks of names.
     *
     * Ordered by id, so two calls that see the same rows answer them in the same order. Without it the
     * database is free to answer in any order, and a caller paging through the result could see the
     * same set twice.
     */
    fun findAllByUrlOrderById(url: String): List<Mset>

    /**
     * the mset with the smallest id that is greater than [id], null when there is none
     *
     * The neighbour above [id] in id order, which is what paging through the sets one at a time is.
     *
     * The `min` over a subquery rather than an `order by id asc` over the whole table, because a
     * query that answers a single [Mset] is held to answering exactly one row: an ordered query with
     * a condition alone would match every set above [id] and fail on all but the first. The subquery
     * states outright which one is meant, and the database still only has to look at the index.
     */
    @Query(
        "select mset from Mset mset " +
            "where mset.id = (select min(above.id) from Mset above where above.id > :id)"
    )
    fun findFirstAboveId(@Param("id") id: Int): Mset?

    /**
     * the mset with the largest id that is smaller than [id], null when there is none
     *
     * The counterpart of [findFirstAboveId], so the two step through the sets in the same order in
     * either direction, and for the same reason it is a `max` subquery rather than an ordered query.
     */
    @Query(
        "select mset from Mset mset " +
            "where mset.id = (select max(below.id) from Mset below where below.id < :id)"
    )
    fun findFirstBelowId(@Param("id") id: Int): Mset?

    /**
     * the msets that hold at least one medium with a bessource in [storageId]
     *
     * An mset carries no storage of its own, so the only way from a set to a storage runs through
     * its media and their bessources: an mset belongs to the storage its files live on. A set with
     * no media, or whose media have no bessource yet, is not found, since nothing about it says
     * where its files are. `distinct` is what keeps a set with several media in the same storage
     * from being reported once per medium.
     */
    @Query(
        "select distinct mset from Mset mset " +
            "join mset.media medium join medium.bessources bessource " +
            "where bessource.storage.id = :storageId"
    )
    fun findByStorageId(@Param("storageId") storageId: Int): List<Mset>

}

