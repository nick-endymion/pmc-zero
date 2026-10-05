package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.RessType
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.service.ThumbnailService
import org.endy.pmczero.to.MsetThumbnailsTO
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/msets")
class MsetRessource(
    val msetService: MsetService,
    val scannerService: ScannerService,
    val locationService: LocationService,
    val thumbnailService: ThumbnailService
) {

    @GetMapping("/{id}")
    fun getMset(@PathVariable id: Int): MsetTO {
        return msetService.findById(id).toTO()
    }

    @GetMapping("/{id}/media")
    fun getMsetWithMedia(@PathVariable id: Int): MsetTO {
        return msetService.findById(id).toTOwithMedia(true)
    }

    @GetMapping("/{id}/ressources-urls")
    fun getMsetWithMediax(@PathVariable id: Int): List<RessourceUrlsTO> {
        return msetService.ressourcesInUse(id)
    }

    /**
     * Creates the thumbnail of every medium of this mset, answering what happened to each of them.
     *
     * One medium that cannot be thumbnailed does not fail the call: a scanned mset routinely holds
     * media with no primary bessource and media whose files are gone, and one of those must not stop
     * the rest from being processed. The answer therefore splits the media three ways, into
     * [MsetThumbnailsTO.thumbnails] (each carrying whether it was generated or was already there),
     * [MsetThumbnailsTO.skipped] (no primary bessource, so nothing to derive one from) and
     * [MsetThumbnailsTO.failures] (attempted and could not be finished, with the reason). Retrying is
     * a matter of calling this again: media that already have a thumbnail are left alone, so only
     * what is in failures is processed a second time.
     *
     * Answers 404 when the mset does not exist.
     *
     * @param force regenerate every thumbnail, even the ones already on disk
     */
    @PostMapping("/{id}/thumbnails")
    fun createThumbnails(
        @PathVariable id: Int,
        @RequestParam(name = "force", required = false, defaultValue = "false") force: Boolean
    ): MsetThumbnailsTO {
        return thumbnailService.createThumbnailsOfMset(id, force)
    }

    @GetMapping("/")
    fun getMsets(@RequestParam searchTerm: String): List<MsetTO> {
        return msetService.search(searchTerm).map { it.toTO() }
    }

    @PostMapping("/")
    fun createMset(@RequestBody msetTO: MsetTO): MsetTO {
        if (msetTO.id != null) throw Exception()
        val saved = msetService.save(msetTO.toEntity())
        // re-read so the response reflects the persisted row
        return msetService.findById(saved.id!!).toTO()
    }

    @PutMapping("/{id}")
    fun saveMset(@PathVariable id: Int, @RequestBody msetTO: MsetTO): MsetTO {
        if (id != msetTO.id) throw Exception()
        msetService.save(msetTO.toEntity())
        // re-read: created_at is updatable=false, so the merged instance does not carry it back
        return msetService.findById(id).toTO()
    }

    @DeleteMapping("/{id}")
    fun deleteMset(@PathVariable id: Int) {
        return msetService.delete(id)
    }

//
//    @GetMapping("/{id}/images/html")
//    fun getImagesHtmlPage(@PathVariable id: Int): String {
//        return msetService.htmlImagePage(id, RessType.PRIMARY)
//    }
//
//    @GetMapping("/{id}/tns/html")
//    fun getTnsHtmlPage(@PathVariable id: Int): String {
//        return msetService.htmlImagePage(id, RessType.TN)
//    }

//    @PostMapping("/scan")
//    fun scan(@RequestBody sts: SourceToScanTO): MsetTO {
//        if (sts.scannerId == null) throw Exception()
//        var mset = scannerService.scan(sts)
//        if (sts.persist == true)
//            mset = msetService.save(mset)
//        return msetService.save(mset).toTO()
//    }

}
