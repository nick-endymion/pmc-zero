package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.service.ThumbnailService
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.MsetThumbnailsTO
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

    /**
     * The set following this one in id order, i.e. the one with the smallest id greater than [id].
     *
     * For stepping through the sets one at a time. The order is the order the rows were created in,
     * which is the only order a set has of its own; nothing about a name or a directory takes part in
     * it, so two sets scanned from the same directory are neighbours if nothing else was created
     * between them.
     *
     * The id does not have to name a set, so the set after a deleted one is still reachable. It does
     * not have to be the last one either.
     *
     * Answers 404 when no set has a greater id, which is how a caller learns it has reached the end.
     * See [MsetService.findAbove].
     */
    @GetMapping("/{id}/up")
    fun getNextMset(@PathVariable id: Int): MsetTO {
        return msetService.findAbove(id).toTO()
    }

    /**
     * The set preceding this one in id order, i.e. the one with the largest id smaller than [id].
     *
     * The counterpart of [getNextMset] in the other direction, and the same in every respect but the
     * way it steps: a deleted id is still answered with the set before it, and 404 means the
     * beginning has been reached. See [MsetService.findBelow].
     */
    @GetMapping("/{id}/down")
    fun getprevMset(@PathVariable id: Int): MsetTO {
        return msetService.findBelow(id).toTO()
    }

    @GetMapping("/{id}/media")
    fun getMsetWithMedia(@PathVariable id: Int): MsetTO {
        return msetService.findById(id).toTOwithMedia(true)
    }

    /**
     * The sets that were imported from [url], in id order.
     *
     * For a page that may have been scraped more than once, which is the normal case rather than the
     * odd one: once with its files, again with `noDownload` to record what it holds, or once per
     * supplier of the same gallery. [MsetTO.supplierId] and [MsetTO.scannerId] are what tell those
     * apart, which is why this answers with the sets and not with a name.
     *
     * Answers an empty list when nothing was ever imported from that url, since a page that has not
     * been scraped yet is a normal thing to ask about and not an error.
     *
     * The media are left out, as in [getMsets], so the answer stays a row per set rather than a
     * gallery per set. [getMsetWithMedia] has them for one set.
     *
     * The url is matched whole, so this is not a search over the pages that mention it.
     */
    @GetMapping("/by-url")
    fun getMsetsByUrl(@RequestParam url: String): List<MsetTO> {
        return msetService.findByUrl(url).map { it.toTO() }
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
