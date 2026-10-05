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
            "where b.storage.id = :storageId and b.ressType = :ressType and b.name in :names"
    )
    fun findNamesOfExistingMedia(
        @Param("storageId") storageId: Int,
        @Param("ressType") ressType: Int,
        @Param("names") names: Collection<String>
    ): List<String>

}

