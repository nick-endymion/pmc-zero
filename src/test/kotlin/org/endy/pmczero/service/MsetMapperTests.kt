package org.endy.pmczero.service

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.mapper.toTOwithMedia
import org.endy.pmczero.model.modern.Mset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the [Mset] mappers, over the fields that say where a set came from.
 *
 * The mappers are written out field by field, so a field added to one side and forgotten on the other
 * is not a compile error: it is a value that reads back as null. [org.endy.pmczero.model.modern.Mset.supplierId]
 * is the field most likely to be lost that way, since it is only ever set by an import and is on none
 * of the older call sites, so it is checked on both directions and on both mappers that build a TO.
 */
class MsetMapperTests {

    private fun mset() = Mset().apply {
        id = 5
        name = "Galerie"
        locationId = 7
        subpath = "2020/august"
        url = "http://example.org/artikel/4711/fotos"
        supplierId = "4711"
        scannnerId = "job-2026-10-08-17"
    }

    @Test
    fun `a set survives being mapped to a TO and back`() {
        val original = mset()

        val roundTripped = original.toTO().toEntity()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.locationId, roundTripped.locationId)
        assertEquals(original.subpath, roundTripped.subpath)
        assertEquals(original.url, roundTripped.url)
        assertEquals(original.supplierId, roundTripped.supplierId)
        assertEquals(original.scannnerId, roundTripped.scannnerId)
    }

    /**
     * Both mappers, since a set is answered by whichever the caller asked for and a field on one but
     * not the other is a field that looks like it is there sometimes.
     */
    @Test
    fun `the supplier and the scan are on the TO of a set with media as well`() {
        assertEquals("4711", mset().toTO().supplierId)
        assertEquals("4711", mset().toTOwithMedia().supplierId)
        assertEquals("job-2026-10-08-17", mset().toTO().scannerId)
        assertEquals("job-2026-10-08-17", mset().toTOwithMedia().scannerId)
    }

    /**
     * A set with neither is a set that did not come from a stored scanner import, and that has to be
     * storable rather than rejected.
     */
    @Test
    fun `carries a set without a supplier and without a scan`() {
        val original = mset().also {
            it.supplierId = null
            it.scannnerId = null
        }

        assertNull(original.toTO().supplierId)
        assertNull(original.toTOwithMedia().scannerId)
        assertNull(original.toTO().toEntity().supplierId)
        assertNull(original.toTO().toEntity().scannnerId)
    }

    /** A blank is kept as it is rather than dropped, so what was recorded is what is read back. */
    @Test
    fun `carries a blank supplier and a blank scan as they are`() {
        val original = mset().also {
            it.supplierId = "  "
            it.scannnerId = "  "
        }

        val roundTripped = original.toTO().toEntity()
        assertEquals("  ", roundTripped.supplierId)
        assertEquals("  ", roundTripped.scannnerId)
    }
}
