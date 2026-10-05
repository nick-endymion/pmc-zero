package org.endy.pmczero.service

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
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

//    -------------

    fun findBesById(id: Int): Bessource {
        return bessourceRepository.findByIdOrNull(id) ?: throw NotFoundException()
    }

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
        val provided = providePhysicalRessources(bessources, generateThumbnail = ressType == RessType.TN)
            ?: return null

        return provided.firstOrNull { it.ressType == ressType.i }?.url
    }

    /**
     * @return the [bessources] with their physical url, or null when a storage or location is missing
     */
    private fun providePhysicalRessources(
        bessources: List<BessourceTO>,
        generateThumbnail: Boolean
    ): List<BessourceTO>? = try {
        locationService.providePhysicalRessources(bessources, "HTTP", generateThumbnail)
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

        val provided = providePhysicalRessources(bessources, generateThumbnail = true) ?: return null

        val primaryUrl = provided.firstOrNull { it.ressType == RessType.PRIMARY.i }?.url
        val tnUrl = provided.firstOrNull { it.ressType == RessType.TN.i }?.url

        return  RessourceUrlsTO(medium.id, medium.name, primaryUrl, tnUrl)
    }

    fun file(id: Int, type: RessType) {

    }

    fun mediumById(id: Int): Medium {
        return mediaRepository.findByIdOrNull(id) ?: throw NotFoundException()
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

