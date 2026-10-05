package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.ThumbnailService
import org.endy.pmczero.to.MediumTO
import org.endy.pmczero.to.ThumbnailTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/media")
class MediumRessource(
    private val mediaService: MediaService,
    private val thumbnailService: ThumbnailService
) {


    @GetMapping("/{id}")
    fun getMedium(@PathVariable id: Int): MediumTO {
        return mediaService.findById(id).toTO()
    }

    /**
     * Creates the thumbnail of this medium and records it as a TN bessource, answering where it was
     * written and how big it is.
     *
     * The file lands in the TN_FS location of the storage the medium sits on, mirroring the
     * subdirectory of the primary file, and any missing directory on the way is created.
     *
     * A thumbnail that is already there is left alone, so this is safe to call over a whole set.
     * Answers 404 when the medium does not exist, and 409 when it cannot be thumbnailed: no primary
     * bessource, no in use MAIN_FS or TN_FS location, a missing or unreadable primary file, a file
     * that is not an image, or a TN location that cannot be written to.
     *
     * @param force regenerate the thumbnail even when one is already there
     */
    @PostMapping("/{id}/thumbnail")
    fun createThumbnail(
        @PathVariable id: Int,
        @RequestParam(name = "force", required = false, defaultValue = "false") force: Boolean
    ): ThumbnailTO {
        return thumbnailService.createThumbnail(id, force)
    }
//    TODO
//    get with Mset (Attributes)

}
