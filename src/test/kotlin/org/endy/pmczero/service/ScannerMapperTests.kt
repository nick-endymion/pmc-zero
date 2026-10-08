package org.endy.pmczero.service

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.modern.Scanner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the [Scanner] mappers.
 *
 * They are written out field by field, so a field added to one side and forgotten on the other is not a
 * compile error: it is a value that reads back as null, which is what these check for. A scanner that
 * loses its url regex is unreachable through [ScannerService.findByUrl] and one that loses its
 * serializaton runs nothing, so a dropped field is a scanner that is there and does not work.
 */
class ScannerMapperTests {

    private fun scanner() = Scanner().apply {
        id = 3
        name = "Image Scraper"
        regex = "https://example\\.org/.*"
        supplierIdentifcator = "https://example\\.org/artikel/([0-9]+)/.*"
        example = "https://example.org/gallery"
        serialization = """{"parser":{"type":"passThrough"},"worker":{"type":"setCreator"}}"""
        valid = true
    }

    @Test
    fun `every field of a scanner survives being mapped to a TO and back`() {
        val original = scanner()

        val roundTripped = original.toTO().toEntity()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.regex, roundTripped.regex)
        assertEquals(original.supplierIdentifcator, roundTripped.supplierIdentifcator)
        assertEquals(original.example, roundTripped.example)
        assertEquals(original.serialization, roundTripped.serialization)
        assertEquals(original.valid, roundTripped.valid)
    }

    /** The two regexes are separate questions about a url, so neither may be read as the other. */
    @Test
    fun `keeps the supplier identifcator apart from the regex`() {
        val to = scanner().toTO()

        assertEquals("https://example\\.org/.*", to.regex)
        assertEquals("https://example\\.org/artikel/([0-9]+)/.*", to.supplierIdentifcator)
    }

    /**
     * A scanner without one is a scanner whose ids are not in its urls, and that has to be storable
     * rather than rejected: leaving it out of the json is the way a client says so.
     */
    @Test
    fun `carries a scanner without a supplier identifcator`() {
        val original = scanner().also { it.supplierIdentifcator = null }

        assertEquals(null, original.toTO().supplierIdentifcator)
        assertEquals(null, original.toTO().toEntity().supplierIdentifcator)
    }

    /** A blank one is kept as it was given rather than dropped, so what was configured is what is read back. */
    @Test
    fun `carries a blank supplier identifcator as it is`() {
        val original = scanner().also { it.supplierIdentifcator = "  " }

        assertEquals("  ", original.toTO().toEntity().supplierIdentifcator)
    }
}
