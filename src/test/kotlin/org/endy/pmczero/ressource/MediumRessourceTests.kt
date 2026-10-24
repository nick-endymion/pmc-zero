package org.endy.pmczero.ressource

import org.endy.pmczero.model.LocationType
import org.endy.pmczero.model.RessType
import org.endy.pmczero.model.modern.Bessource
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.repository.BessourceRepository
import org.endy.pmczero.repository.LocationRepository
import org.endy.pmczero.repository.MediaRepository
import org.endy.pmczero.repository.StorageRepository
import org.endy.pmczero.service.LocationService
import org.endy.pmczero.service.MediaDeletionService
import org.endy.pmczero.service.MediaService
import org.endy.pmczero.service.StorageService
import org.endy.pmczero.service.ThumbnailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

/**
 * What `GET /api/media?ranking=` answers, over the real [MediaService] and [LocationService].
 *
 * Filling the location type and url of the bessources is exactly what those two own, so only the
 * repositories and the services not under test are mocked.
 */
class MediumRessourceTests {

    private val mediaRepository: MediaRepository = mock()
    private val storageRepository: StorageRepository = mock()

    private val mediaService = MediaService(
        mediaRepository,
        mock<BessourceRepository>(),
        LocationService(
            mock<LocationRepository>(),
            StorageService(storageRepository),
            mock<BessourceRepository>()
        )
    )
    private val ressource = MediumRessource(
        mediaService,
        mock<ThumbnailService>(),
        mock<MediaDeletionService>()
    )

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(ressource).build()
    }

    /** The mapping itself, including the enum and url fields as they appear in json. */
    @Test
    fun `is mapped to GET api media with the ranking as request parameter`() {
        givenStorage(httpStorage())
        whenever(mediaRepository.findByRanking(3)).thenReturn(
            listOf(medium(3, bessource("doc.pdf", RessType.PRIMARY)))
        )

        mvc.perform(get("/api/media").param("ranking", "3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].ranking").value(3))
            .andExpect(jsonPath("$[0].bessources.length()").value(2))
            .andExpect(jsonPath("$[0].bessources[0].locationType").value("MAIN_HTTP"))
            .andExpect(jsonPath("$[0].bessources[0].url").value("http://example.org/main/doc.pdf"))
            .andExpect(jsonPath("$[0].bessources[1].locationType").value("TN_HTTP"))
            .andExpect(jsonPath("$[0].bessources[1].url").value("http://example.org/tn/doc.pdf"))
    }

    @Test
    fun `answers the media carrying the requested ranking`() {
        givenStorage(httpStorage())
        whenever(mediaRepository.findByRanking(3)).thenReturn(listOf(medium(3)))

        val result = ressource.getMediaByRanking(3)

        assertEquals(1, result.size)
        assertEquals(3, result[0].ranking)
        verify(mediaRepository).findByRanking(3)
    }

    @Test
    fun `fills location type and url of every bessource of the media`() {
        givenStorage(httpStorage())
        whenever(mediaRepository.findByRanking(3)).thenReturn(
            listOf(
                medium(
                    3,
                    bessource("doc.pdf", RessType.PRIMARY),
                    bessource("doc_thumb.jpg", RessType.TN),
                    bessource("doc.jpg", RessType.PIC)
                )
            )
        )

        val bessources = ressource.getMediaByRanking(3).single().bessources

        assertEquals(
            listOf(LocationType.MAIN_HTTP, LocationType.TN_HTTP, LocationType.MAIN_HTTP),
            bessources.map { it.locationType }
        )
        assertEquals(
            listOf(
                "http://example.org/main/doc.pdf",
                "http://example.org/tn/doc_thumb.jpg",
                "http://example.org/main/doc.jpg"
            ),
            bessources.map { it.url }
        )
    }

    @Test
    fun `answers an empty list for a ranking no medium carries`() {
        whenever(mediaRepository.findByRanking(9)).thenReturn(emptyList())

        assertTrue(ressource.getMediaByRanking(9).isEmpty())
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

    private fun medium(ranking: Int, vararg bessources: Bessource): Medium {
        val storage = httpStorage()
        bessources.forEach { it.storage = storage }
        return Medium().apply {
            id = 5
            name = "medium-5"
            this.ranking = ranking
            this.bessources = bessources.toMutableList()
        }
    }

    private fun bessource(name: String, ressType: RessType): Bessource = Bessource().apply {
        this.name = name
        this.ressType = ressType.i
    }

    private fun givenStorage(storage: Storage) {
        whenever(storageRepository.findById(storage.id!!)).thenReturn(Optional.of(storage))
    }
}
