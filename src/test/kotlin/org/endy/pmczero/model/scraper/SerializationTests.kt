package org.endy.pmczero.model.scraper

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkClass
import io.mockk.spyk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.endy.pmczero.model.modern.Location
import org.endy.pmczero.model.modern.Medium
import org.endy.pmczero.model.modern.Storage
import org.endy.pmczero.service.Downloader
import org.endy.pmczero.service.LocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import kotlin.test.assertEquals

class SerializationTests {

    @MockK
    public lateinit var locationService: LocationService

    @MockK
    public lateinit var downloader: Downloader

    @BeforeEach
    fun setUp() = MockKAnnotations.init(this)

    @Test
    fun test() {
        var mediuam = Medium()
        var location = Location().also { it.name = "http://"; it.storage = Storage() }

        every {
            locationService.getLocationStartingWith(any())
        } returns location

        every {
            downloader.getAsString(any())
        } returns "<html><title>TEST</title></html>"


        val scanner = Scanner(
            SimpleParser("(.*fa.*)".toRegex()),
            SetCreator()
        )

        println(Json.encodeToString(scanner))

    }
}
