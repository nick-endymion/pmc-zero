package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.Scheme
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.to.BessourceTO
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class MediaService(
    private val mediaRepository: MediaRepository,
    private val bessourceRepository: BessourceRepository,
    private val locationService: LocationService
) {

    fun findById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    fun save(medium: Medium): Medium {
        return mediaRepository.save(medium)
    }

    fun delete(id: Int) {
        mediaRepository.delete(findById(id))
    }

    /**
     * Sets or clears [Medium.deleted] on the medium with [id], so the row survives either way.
     *
     * A flag rather than a deletion, see [Medium.deleted]: the row stays and its files are left
     * where they are. Clearing it again brings the medium back into everything that treats the
     * stored media as the known ones, the file listing among them, which is what makes this a way
     * to undo a mark.
     *
     * Re-read after the save, because the response has to reflect the persisted row rather than the
     * instance that was sent in.
     *
     * @throws NotFoundException when no medium with that id exists
     */
    fun setDeleted(id: Int, deleted: Boolean): Medium {
        val medium = findById(id)
        medium.deleted = deleted
        mediaRepository.save(medium)
        // createdAt is updatable=false, so the merged instance does not carry it back
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    /**
     * Sets or clears [Medium.ranking] on the medium with [id].
     *
     * A null [ranking] takes the medium out of the order altogether, which is the same shape of
     * operation as [setDeleted]: what it writes is one nullable column and the caller says whether
     * there is a value or none. No range is enforced here. [Medium.ranking] is a plain integer on
     * purpose, two media of one set may share it and the column does not renumber anything, so a
     * bound belongs to whatever is doing the ranking rather than to the column.
     *
     * Re-read after the save, for the reason given on [setDeleted].
     *
     * @throws NotFoundException when no medium with that id exists
     */
    fun setRanking(id: Int, ranking: Int?): Medium {
        val medium = findById(id)
        medium.ranking = ranking
        mediaRepository.save(medium)
        // createdAt is updatable=false, so the merged instance does not carry it back
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

//    -------------

    fun url(id: Int, ressType: RessType): String {
        val medium = findById(id)
        return url(medium, ressType)
    }

    fun url(medium: Medium, ressType: RessType): String {
        return provideUrl(medium, ressType)
            ?: throw Exception("no url found for medium ${medium.id} and resource type $ressType")
    }

    /**
     * Provides the url of the [ressType] ressource of [medium] via
     * [LocationService.providePhysicalRessources], which owns the ressource type -> location type
     * mapping and the url building.
     *
     * A thumbnail is not necessarily available in the TN location, so the main location is used as
     * fallback.
     *
     * @return null when [medium] has no such ressource or it cannot be located
     */
    private fun provideUrl(medium: Medium, ressType: RessType): String? =
        if (ressType == RessType.TN) {
            provideUrl(medium, ressType, forcedLocationType = null)
                ?: provideUrl(medium, ressType, forcedLocationType = LocationType.MAIN_HTTP)
        } else {
            provideUrl(medium, ressType, forcedLocationType = forcedLocationType(ressType))
        }

    fun getUrlFor(medium: Medium, ressType: RessType, locationType: LocationType): String? {
        return provideUrl(medium, ressType, forcedLocationType = locationType)
    }

    /**
     * Provides the url of the [ressType] ressource of [medium] via
     * [LocationService.providePhysicalRessources], which owns the ressource type -> location type
     * mapping and the url building.
     *
     * For [RessType.TN] the thumbnailed bessource is used, or the primary one when the medium has
     * none, in which case the thumbnail is derived from it.
     *
     * @param forcedLocationType location type to use instead of the derived one
     * @return null when [medium] has no such ressource or it cannot be located
     */
    private fun provideUrl(
        medium: Medium,
        ressType: RessType,
        forcedLocationType: LocationType?
    ): String? {
        val allBessources = medium.toTO().bessources

        // for TN the primary bessource is needed as well, providePhysicalRessources derives the
        // thumbnail from it when the medium has no thumbnailed bessource
        val bessources = allBessources.filter {
            it.ressType == ressType.i || (ressType == RessType.TN && it.ressType == RessType.PRIMARY.i)
        }

        if (bessources.isEmpty()) return null

        if (forcedLocationType != null)
            bessources.forEach { it.locationType = forcedLocationType }

        // only for TN, where a missing thumbnailed bessource is derived from the primary one
        val provided = providePhysicalRessources(bessources, provideThumbnailsDynamically = ressType == RessType.TN)
            ?: return null

        return provided.firstOrNull { it.ressType == ressType.i }?.url
    }

    /**
     * @return the [bessources] with their physical url, or null when a storage or location is missing
     */
    private fun providePhysicalRessources(
        bessources: List<BessourceTO>,
        provideThumbnailsDynamically: Boolean = true
    ): List<BessourceTO>? = try {
        locationService.providePhysicalRessources(bessources, provideThumbnailsDynamically = provideThumbnailsDynamically)
    } catch (e: NotFoundException) {  // missing storage or location
        null
    }

    /**
     * providePhysicalRessources derives the location type for PRIMARY (MAIN_HTTP) and TN (TN_HTTP),
     * the remaining ressource types (PIC, URL, FOLDER) are stored in the main location.
     */
    private fun forcedLocationType(ressType: RessType): LocationType? = when (ressType) {
        RessType.PRIMARY, RessType.TN -> null
        else -> LocationType.MAIN_HTTP
    }

    /**
     * Provides the physical urls of [medium], the original and the thumbnail, via
     * [LocationService.providePhysicalRessources].
     *
     * A thumbnailed bessource is derived from the primary one when the medium has none.
     *
     * @return null when [medium] does not provide both urls, e.g. for migrated media of type
     * (legacy) folder or when no location is in use.
     */
    fun ressourceUrls(medium: Medium): RessourceUrlsTO? {
        // the remaining bessource types (PIC, URL, FOLDER) have no location assigned by
        // providePhysicalRessources
        val bessources = medium.toTO().bessources
            .filter { it.ressType in listOf(RessType.PRIMARY.i, RessType.TN.i) }

        if (bessources.none { it.ressType == RessType.PRIMARY.i }) return null

        val provided = providePhysicalRessources(bessources) ?: return null

        val primaryUrl = provided.firstOrNull { it.ressType == RessType.PRIMARY.i }?.url
        val tnUrl = provided.firstOrNull { it.ressType == RessType.TN.i }?.url

        if (primaryUrl == null || tnUrl == null) return null

        // mtype passed on so a caller can tell what kind of medium this is without reading the
        // extension out of the name. Null for a row written before the column said anything.
        // ranking likewise, so the viewer can show the order the medium already stands in rather
        // than ask for a number without saying what is on the row today.
        return RessourceUrlsTO(
            medium.id, medium.name, primaryUrl, tnUrl, medium.mtype, medium.ranking
        )
    }

    fun file(id: Int, type: RessType) {

    }

    fun mediumById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

    /**
     * The media carrying [ranking], in id order, across all sets.
     *
     * See [org.endy.pmczero.model.modern.Medium.ranking]: a ranking is a number a caller put on a
     * row, so two media of different sets may share one.
     */
    fun findByRanking(ranking: Int): List<Medium> = mediaRepository.findByRanking(ranking)

    /**
     * [bessources] with their location type and url filled in, the way
     * [LocationService.providePhysicalRessources] resolves them, and completed with a derived
     * thumbnailed bessource when the medium has a primary one but no stored thumbnail.
     *
     * A thumbnail is derived from the primary bessource exactly as the media of a set are served, so
     * a caller sees the same bessources whichever endpoint it asks. The remaining ressource types
     * (PIC, URL, FOLDER) have no location type of their own and are resolved against the main
     * location, the same way [url] resolves them.
     *
     * A bessource whose storage or location is missing keeps a null url instead of failing the whole
     * answer, so a medium that cannot be fully resolved is still handed out.
     */
    fun providePhysicalBessources(bessources: List<BessourceTO>): List<BessourceTO> {
        bessources.filter { it.ressType != RessType.PRIMARY.i && it.ressType != RessType.TN.i }
            .forEach { it.locationType = LocationType.MAIN_HTTP }

        // only a primary bessource has something to derive a thumbnail from
        val deriveThumbnail = bessources.any { it.ressType == RessType.PRIMARY.i } &&
                bessources.none { it.ressType == RessType.TN.i }

        return try {
            locationService.providePhysicalRessources(
                bessources,
                Scheme.HTTP,
                provideThumbnailsDynamically = deriveThumbnail
            )
        } catch (e: NotFoundException) {  // missing storage
            bessources
        }
    }

    fun urlByMediumId(id: Int, type: RessType): String {
        val medium = mediumById(id)
//        val mfile =  medium.mfiles.first() // Todo > filter logic by resstype
//        val storage = mfile.folder?.storage
//        if (storage != null) {
//            val locations = storage.locations.filter { loc -> loc.inuse == 1.toByte() }
//            val location = locations.first { loc -> loc.typ == 1 }
//            return locationService.url(mfile, location)
//        }
        return "N/A"
    }

}

