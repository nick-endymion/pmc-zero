package org.endy.pmczero.ressource

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Mset
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.StorageRepository
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MsetService
import org.endy.pmczero.service.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * What `GET /api/msets/{id}/media` answers, focused on the bessources under the media.
 *
 * The endpoint runs the real [LocationService], since filling location type and url is exactly what
 * it delegates to it; only the repositories and the two services that provide the set are mocked.
 */
class MsetMediaRessourceTests {

    private val msetService: MsetService = mock()
    private val storageRepository: StorageRepository = mock()

    private val locationService = LocationService(
        mock<LocationRepository>(),
        StorageService(storageRepository),
        mock<BessourceRepository>()
    )
    private val ressource = MsetRessource(msetService, locationService, mock())

    @Test
    fun `fills location type and url of a primary and a thumbnailed bessource`() {
        val storage = httpStorage()
        givenStorage(storage)
        whenever(msetService.findById(1)).thenReturn(
            mset(storage, primary("a.pdf"), tn("a_thumb.jpg"))
        )

        val bessources = ressource.getMsetWithMedia(1).media!!.single().bessources

        assertEquals(
            listOf(LocationType.MAIN_HTTP, LocationType.TN_HTTP),
            bessources.map { it.locationType }
        )
        assertEquals(
            listOf("http://example.org/main/a.pdf", "http://example.org/tn/a_thumb.jpg"),
            bessources.map { it.url }
        )
    }

    /**
     * The remaining ressource types have no location type of their own and are resolved against the
     * main location instead of failing the answer.
     */
    @Test
    fun `fills a picture bessource against the main location`() {
        val storage = httpStorage()
        givenStorage(storage)
        whenever(msetService.findById(1)).thenReturn(
            mset(storage, primary("a.pdf"), bessource("a.jpg", RessType.PIC))
        )

        val bessources = ressource.getMsetWithMedia(1).media!!.single().bessources

        assertEquals(
            listOf(LocationType.MAIN_HTTP, LocationType.MAIN_HTTP, LocationType.TN_HTTP),
            bessources.map { it.locationType }
        )
        assertEquals(
            listOf(
                "http://example.org/main/a.pdf",
                "http://example.org/main/a.jpg",
                "http://example.org/tn/a.pdf"
            ),
            bessources.map { it.url }
        )
    }

    /** A medium without a stored thumbnail gets the one derived from the primary bessource. */
    @Test
    fun `derives a thumbnailed bessource when the medium has none`() {
        val storage = httpStorage()
        givenStorage(storage)
        whenever(msetService.findById(1)).thenReturn(mset(storage, primary("a.pdf")))

        val bessources = ressource.getMsetWithMedia(1).media!!.single().bessources

        assertEquals(2, bessources.size)
        assertEquals(LocationType.TN_HTTP, bessources[1].locationType)
        assertEquals("http://example.org/tn/a.pdf", bessources[1].url)
    }

    /**
     * A medium that has no bessource at all is answered rather than failing the whole set.
     *
     * Such a medium exists: a set scanned from a page holds one medium per picture plus one for
     * the page itself, and that one has no file of its own. Resolving it asks for a thumbnail that
     * cannot be derived, since there is no primary bessource to derive it from.
     */
    @Test
    fun `answers a medium that has no bessource at all`() {
        whenever(msetService.findById(1)).thenReturn(msetOf(medium()))

        val media = ressource.getMsetWithMedia(1).media!!

        assertEquals(1, media.size)
        assertTrue(media.single().bessources.isEmpty())
    }

    /** A medium whose bessources carry no primary one keeps the bessources it has. */
    @Test
    fun `answers a medium whose bessources carry no primary one`() {
        val storage = httpStorage()
        givenStorage(storage)
        whenever(msetService.findById(1)).thenReturn(msetOf(medium(storage, tn("a_thumb.jpg"))))

        val bessources = ressource.getMsetWithMedia(1).media!!.single().bessources

        // no thumbnail is derived, since there is no primary bessource to derive it from
        assertEquals(1, bessources.size)
        assertEquals(LocationType.TN_HTTP, bessources.single().locationType)
        assertEquals("http://example.org/tn/a_thumb.jpg", bessources.single().url)
    }

    /**
     * A medium whose storage is gone is answered with a null url rather than failing the set.
     *
     * The bessource points at a storage that does not exist any more, so no url can be built for
     * it. That is a property of that one medium, not of the set, so the other media of the set
     * must still be answered.
     */
    @Test
    fun `answers a medium whose storage is gone`() {
        // a storage that is not registered, so looking it up fails
        val gone = Storage().apply { id = 99; name = "gone" }
        whenever(msetService.findById(1)).thenReturn(msetOf(medium(gone, primary("a.pdf"))))

        val bessources = ressource.getMsetWithMedia(1).media!!.single().bessources

        assertEquals(1, bessources.size)
        assertNull(bessources.single().url)
    }

    /** One medium that cannot be resolved does not hide the media that can. */
    @Test
    fun `answers the other media when one of them has no bessource`() {
        val storage = httpStorage()
        givenStorage(storage)
        whenever(msetService.findById(1)).thenReturn(
            msetOf(
                medium(storage, primary("a.pdf")),
                medium()
            )
        )

        val bessources = ressource.getMsetWithMedia(1).media!!.flatMap { it.bessources }

        assertEquals(2, bessources.size)
        assertEquals("http://example.org/main/a.pdf", bessources[0].url)
        assertEquals("http://example.org/tn/a.pdf", bessources[1].url)
    }

    private fun httpStorage(): Storage = Storage().apply {
        id = 1
        name = "storage-1"
        locations = listOf(
            location("http://example.org/main", LocationType.MAIN_HTTP),
            location("http://example.org/tn", LocationType.TN_HTTP)
        )
    }

    private fun location(uri: String, locationType: LocationType): Location = Location().apply {
        name = uri
        this.uri = uri
        this.locationType = locationType.idx
        inuse = 1
    }

    private fun mset(storage: Storage, vararg bessources: Bessource): Mset =
        msetOf(medium(storage, *bessources))

    /** A set holding exactly the given media, so a test can name one medium rather than build one. */
    private fun msetOf(vararg media: Medium): Mset = Mset().apply {
        id = 1
        name = "set-1"
        this.media = media.toMutableList()
    }

    /** A medium carrying the given bessources, each of them on [storage]. */
    private fun medium(storage: Storage? = null, vararg bessources: Bessource): Medium = Medium().apply {
        id = 5
        name = "a.pdf"
        if (storage != null) bessources.forEach { it.storage = storage }
        this.bessources = bessources.toMutableList()
    }

    private fun primary(name: String): Bessource = bessource(name, RessType.PRIMARY)

    private fun tn(name: String): Bessource = bessource(name, RessType.TN)

    private fun bessource(name: String, ressType: RessType): Bessource = Bessource().apply {
        this.name = name
        this.ressType = ressType.i
    }

    private fun givenStorage(storage: Storage) {
        whenever(storageRepository.findById(storage.id!!)).thenReturn(Optional.of(storage))
    }
}
