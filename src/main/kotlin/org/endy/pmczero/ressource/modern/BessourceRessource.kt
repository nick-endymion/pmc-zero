package org.endy.pmczero.ressource.modern

import org.endy.pmczero.exception.NotFoundException
import org.endy.pmczero.model.RessType
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.service.BessourceFiles
import org.endy.pmczero.service.MediaService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

/**
 * What can be got at over http about the files of this application.
 *
 * Two ways of asking, since there are two things a caller has at hand: a medium, where
 * [getMfilePic] and [afa] answer the url its files are stored under, and a storage with a name, where
 * [file] answers the bytes of that name.
 */
@RestController
class BessourceRessource(
    val mfilesRepository: LocationRepository,
    private val mediaService: MediaService,
    private val bessourceFiles: BessourceFiles
) {

//    @GetMapping("/mfiles/to")
//    fun getMfilesTO(): Iterable<MfileTO> {
////        return mfilesRepository.getWithfolder().map { m -> m.toTO() }
//        return mfilesRepository.findAll().map { m -> m.toTO() }
//    }

//    @GetMapping("/mfiles/{id}")
//    fun getMfile(@PathVariable id: Int): MfilesEntity? {
//        return mfilesRepository.findByIdOrNull(id)
//    }

    @GetMapping("/medium/{id}/url")
    fun getMfilePic(@PathVariable id: Int): String {
        return mediaService.url(id,RessType.PRIMARY)
    }

    @GetMapping("/medium/{id}/binary")
    fun showMfilePic(@PathVariable id: Int): RedirectView
    {
        return RedirectView( mediaService.url(id,RessType.PRIMARY))
    }

    @GetMapping("/medium/{id}/tn/url")
    fun afa(@PathVariable id: Int): String {
        return mediaService.url(id, RessType.TN)
    }

    /**
     * The file below the MAIN_FS location of [storageId] that the rest of the url names, as the bytes
     * on disk.
     *
     * `main` is the MAIN_FS location of the storage, i.e. the folder the primary files live in, which
     * is where a `a.bessources.name` of type PRIMARY points. The path after the storage is therefore
     * the one the file was stored under: a bessource recorded as `imagegap4/abc/984580928.jpg` on a
     * storage whose MAIN_FS location is at `s:/locations/loc` is served from
     * `/main/{thatStorage}/imagegap4/abc/984580928.jpg`.
     *
     * It is served outside `/api` and as the file itself rather than as json about it, so that a url
     * of this shape can go into an `<img src>` or an `<a href>` as it is.
     *
     * Everything behind `/main/{storageId}/` is the name of the file, slashes included, since a
     * bessource name is a path below the location rather than a file name alone. The mapping is a
     * wildcard path and the name is read off the request rather than taken as a path variable, because
     * this application matches urls with the ant matcher (springfox asks for it), and that matcher has
     * no `{*name}` for the rest of a path: a name with a slash in it would either fail to match or
     * match with an empty variable.
     *
     * The name is percent decoded before it is used, so a stored `bilder/mein bild.jpg` is requested
     * as `/main/1/bilder/mein%20bild.jpg`. A name holding a `#` or a `?` has to be encoded as well,
     * since either would otherwise end the url at the client.
     *
     * The content type is the one the extension of the file on disk asks for and falls back to
     * `application/octet-stream` for an extension nothing knows, so an unknown file is delivered rather
     * than refused. The length is answered, and `Last-Modified` comes with it, so a client may send
     * `If-Modified-Since` and get a 304 rather than the bytes again.
     *
     * A file is served whatever is or is not recorded for it: this reads a name off disk and does not
     * look for a medium, so a file in that folder without a row is served, and a row whose file has
     * been deleted since answers 404 like any other. A caller that wants only recorded media should
     * ask [getMfilePic] for the urls of a medium instead.
     *
     * A name that climbs out of the location answers the same 404 as a name that is not there, since
     * the answer would otherwise tell a caller which folders sit next to the location.
     *
     * Answers 404 for a file that is not there, a storage without an in use MAIN_FS location, a name
     * naming a folder rather than a file, a storage id that names no storage, and a name the url does
     * not spell properly.
     */
    @GetMapping("/main/{storageId}/**")
    fun file(
        @PathVariable storageId: Int,
        request: HttpServletRequest
    ): ResponseEntity<FileSystemResource> {
        val file = bessourceFiles.mainFileOf(storageId, nameOf(storageId, request)) ?: throw NotFoundException()

        return ResponseEntity.ok()
            .contentType(MediaTypeFactory.getMediaType(file.name).orElse(MediaType.APPLICATION_OCTET_STREAM))
            .contentLength(file.length())
            .body(FileSystemResource(file))
    }

    /**
     * The name asked for: what stands behind `/main/{storageId}/` in the url, decoded.
     *
     * Read off the request uri rather than off the matched pattern, since this application matches
     * urls with the ant matcher, which hands the rest of a path to nobody. `requestURI` rather than
     * `servletPath` or `pathInfo`, since which of those two holds the path is left to the container:
     * the uri is the one that is the same either way. It is not decoded by the container either, so
     * it is decoded here once.
     *
     * The context path is taken off first, so an application deployed below one serves the same urls.
     *
     * Blank for a url that carries nothing after the storage, and blank for one that cannot be decoded
     * at all, e.g. a name ending in a `%`. Both answer nothing to look for rather than throwing, since a
     * name is whatever the client wrote.
     */
    private fun nameOf(storageId: Int, request: HttpServletRequest): String {
        val path = request.requestURI.removePrefix(request.contextPath)
        val name = path.substringAfter("/main/$storageId/", missingDelimiterValue = "")

        return try {
            UriUtils.decode(name, StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            ""
        }
    }

//    @GetMapping("/medium/{id}/image")
//    fun getMediumPic(@PathVariable id: Int): String {
//        return mediaService.urlByMediumId(id,RessType.PIC)
//    }
}