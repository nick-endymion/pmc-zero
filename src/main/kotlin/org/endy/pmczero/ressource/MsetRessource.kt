package org.endy.pmczero.ressource

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.ScannerService
import org.endy.pmczero.service.ThumbnailService
import org.endy.pmczero.to.MsetDeletionTO
import org.endy.pmczero.to.MsetTO
import org.endy.pmczero.to.MsetThumbnailsTO
import org.endy.pmczero.to.RessourceUrlsTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/msets")
class MsetRessource(
    val msetService: MsetService,
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
     * The sets that hold at least one file this set holds as well, in id order.
     *
     * Sets related by what they contain rather than by anything about them: an archive copied onto a
     * second medium, a gallery imported twice, a directory scanned into two sets at different times.
     * Nothing about the sets themselves says they are related, so the only way to find them is over the
     * files: two sets whose bessources name the same file on the same storage.
     *
     * A file is shared rather than duplicated, so the two sets both point at one row rather than at two
     * rows under one name. The case where a name is held twice on one storage is
     * `GET /api/storages/{id}/duplicate-bessources`.
     *
     * The set asked about is not in its own answer. It shares every one of its files with itself, so
     * leaving it in would put it in every answer and make every caller filter it out.
     *
     * A name on two different storages does not relate two sets, since those are two files that are
     * merely called alike, and a medium marked deleted does not either, on either side: its files are
     * unknown to a scan again, so it is not holding anything as far as this goes.
     *
     * The media are left out, as in [getMsetsByUrl], so the answer stays a row per set.
     *
     * Answers an empty list for a set that shares nothing with another, which is the normal state of a
     * gallery that was imported once. Answers 404 for an id that names no set.
     */
    @GetMapping("/{id}/shares-files")
    fun getMsetsSharingFilesWith(@PathVariable id: Int): List<MsetTO> {
        return msetService.findSetsSharingFilesWith(id).map { it.toTO() }
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

    /**
     * The sets carrying exactly the given tag, in id order.
     *
     * The tag is a whole value rather than a fragment, since the words of a tag are its own business:
     * a search for `Heart` does not answer with the sets tagged `Hearts`. Which is also why this is
     * its own path rather than another spelling of the search term on `GET /api/msets/`: the term is
     * matched inside a name, and folding a tag into it would make one call answer a question about
     * two different columns.
     *
     * Answers 200 with an empty list for a tag no set carries. A tag nobody has used is a tag a caller
     * is about to use, and a 404 here would read as though the tag itself were wrong.
     *
     * @param tag the exact tag to look for
     */
    @GetMapping("/tag")
    fun getMsetsByTag(@RequestParam tag: String): List<MsetTO> {
        return msetService.searchByTag(tag).map { it.toTO() }
    }

    /**
     * Every tag in use across the sets, alphabetically.
     *
     * The words alone rather than a count per tag, which is what a page listing them needs to draw: it
     * shows what can be clicked, and the sets behind each are the search this page runs when one is.
     *
     * Distinct on the stored spelling, so `Hearts` and `hearts` are two answers, since they are two
     * tags in [org.endy.pmczero.model.modern.Mset.tags]. See [MsetRepository.findAllTags].
     *
     * Answers 200 with an empty list when nothing is tagged yet.
     */
    @GetMapping("/tags")
    fun getAllTags(): List<String> {
        return msetService.allTags()
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

    /**
     * Deletes this mset as `DELETE /api/msets/{id}` does, and moves the files of every medium of it
     * that is marked deleted below the `DELETED` folder of their location before the set goes.
     *
     * The files have to be moved first, since deleting a set cascades to the bessources of its media
     * and a name that is gone from the database is a file that can no longer be found.
     *
     * Only the media that carry the mark, see [org.endy.pmczero.model.modern.Medium.deleted]: a
     * medium nobody marked goes with the set as a record and its files stay where they are, which is
     * what a plain set deletion does and what marking is for. The files are moved rather than erased,
     * so a deletion that was a mistake can still be undone by hand as long as nobody has cleaned the
     * folder up. See [org.endy.pmczero.service.MediaDeletionService].
     *
     * Answers what happened to each medium that was marked, and over all of them how many files were
     * moved and how many were left alone.
     *
     * Answers 404 when the mset does not exist, and 409 when one of the files to be moved is already
     * there under its new name, in which case no medium is deleted and the set stays.
     */
    @DeleteMapping("/{id}/includingFilesTagged")
    fun deleteMsetIncludingFilesTagged(@PathVariable id: Int): MsetDeletionTO {
        return msetService.deleteIncludingMarkedFiles(id)
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
