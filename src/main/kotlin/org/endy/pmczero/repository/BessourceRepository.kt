package org.endy.pmczero.repository


import org.endy.pmczero.model.modern.Bessource
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface BessourceRepository : CrudRepository<Bessource, Int> {


//    @Query("select m from MfilesEntity m join fetch m.folder")
//    fun getWithfolder() : List<MfilesEntity>

    /**
     * the names of the primary bessources of [storageId] that already point at an existing medium
     * and whose name is one of [names], so the names that are still missing can be told apart
     * from the ones that are already stored
     *
     * Joining the medium is what makes this an existence check for a whole medium with its
     * bessource: a bessource without a medium is not a medium that has been scanned.
     */
    @Query(
        "select b.name from Bessource b join b.medium m " +
            "where b.storage.id = :storageId and b.ressType = :ressType and b.name in :names " +
            // a medium marked as deleted is not acted on any more, so its files are unknown to a
            // scan again and can be created anew
            "and (m.deleted is null or m.deleted = false)"
    )
    fun findNamesOfExistingMedia(
        @Param("storageId") storageId: Int,
        @Param("ressType") ressType: Int,
        @Param("names") names: Collection<String>
    ): List<String>

    /**
     * the ids of the bessources on [storageId], of any ressource type
     *
     * Ids rather than rows, for a caller that walks a whole storage: it looks at each file one at a
     * time and has no use for the rest of a bessource, and a storage of a hundred thousand files does
     * not fit in memory as rows where the ids are a fraction of that.
     */
    @Query("select b.id from Bessource b where b.storage.id = :storageId")
    fun findIdsOfStorage(@Param("storageId") storageId: Int): List<Int>

    /**
     * the bessources of [storageId] that share their name with at least one other bessource of the
     * same storage, of any ressource type
     *
     * The whole colliding group rather than the names, since what a caller does about a duplicate is
     * done to a row: which of the two is the medium that stays. Grouping is therefore left to the
     * names this returns rather than folded into the answer, which is also why [Bessource.medium] is
     * fetched rather than joined: the caller needs the id of the medium behind every row it gets.
     *
     * A duplicate is two rows naming the same file on one storage, so a same name on a *different*
     * storage is not one: those are two files that happen to be called the same, which is what the
     * per storage scoping of this query is for.
     *
     * A null name is not a duplicate of anything. `group by` would fold every nameless row of a
     * storage into one group and report it as a collision of a name that does not exist, so they are
     * excluded here instead. An empty name is left in: it is a name somebody wrote down, and a row
     * that says nothing of where its file is may well be a row that was copied from another.
     *
     * Ordered by name and id, so the members of a group come back next to each other and the same
     * call twice answers the same thing.
     */
    @Query(
        "select b from Bessource b join fetch b.medium m " +
            // the same filter as the subquery below, which decides which names count: a row excluded
            // here but counted there would be answered as a duplicate of a group it is not part of
            "where b.storage.id = :storageId and b.name is not null " +
            "and (m.deleted is null or m.deleted = false) " +
            "and b.name in (" +
            "    select b2.name from Bessource b2 join b2.medium m2" +
            "    where b2.storage.id = :storageId and b2.name is not null" +
            "    and (m2.deleted is null or m2.deleted = false)" +
            "    group by b2.name having count(b2) > 1" +
            ") " +
            "order by b.name, b.id"
    )
    fun findDuplicatesOfStorage(@Param("storageId") storageId: Int): List<Bessource>

}

