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
        scannnerId = 12
        tags = mutableListOf("Hearts", "Aces", "matti")
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
        assertEquals(original.tags, roundTripped.tags)
    }

    /**
     * The tags of a set are written in the order they were given, since a caller that puts them in
     * order is showing them in order.
     */
    @Test
    fun `keeps the tags in the order they were given`() {
        assertEquals(listOf("Hearts", "Aces", "matti"), mset().toTO().tags)
        assertEquals(listOf("Hearts", "Aces", "matti"), mset().toTOwithMedia().tags)
    }

    /** A set nobody described has no tags rather than none of the field, so a caller can read one list. */
    @Test
    fun `carries a set without tags`() {
        val original = mset().also { it.tags = mutableListOf() }

        assertEquals(emptyList(), original.toTO().tags)
        assertEquals(emptyList(), original.toTO().toEntity().tags)
    }

    /**
     * The list of the TO is copied rather than shared, since a TO may be mapped to two sets and the
     * second one must not end up writing the tags of the first.
     */
    @Test
    fun `does not share its tag list with the set it built`() {
        val to = mset().toTO()

        val first = to.toEntity()
        val second = to.toEntity()

        first.tags.add("spaet")

        assertEquals(listOf("Hearts", "Aces", "matti"), second.tags)
    }

    /**
     * Both mappers, since a set is answered by whichever the caller asked for and a field on one but
     * not the other is a field that looks like it is there sometimes.
     */
    @Test
    fun `the supplier and the scanner are on the TO of a set with media as well`() {
        assertEquals("4711", mset().toTO().supplierId)
        assertEquals("4711", mset().toTOwithMedia().supplierId)
        assertEquals(12, mset().toTO().scannerId)
        assertEquals(12, mset().toTOwithMedia().scannerId)
    }

    /**
     * A set with neither is a set that did not come from a stored scanner import, and that has to be
     * storable rather than rejected.
     */
    @Test
    fun `carries a set without a supplier and without a scanner`() {
        val original = mset().also {
            it.supplierId = null
            it.scannnerId = null
            it.tags = mutableListOf()
        }

        assertNull(original.toTO().supplierId)
        assertNull(original.toTOwithMedia().scannerId)
        assertNull(original.toTO().toEntity().supplierId)
        assertNull(original.toTO().toEntity().scannnerId)
        assertEquals(emptyList(), original.toTO().toEntity().tags)
    }

    /**
     * The two ids are different things, so neither may be read as the other: a supplier is whatever a
     * site calls the thing its url belongs to, a scanner is a row of this application's own table.
     */
    @Test
    fun `keeps the supplier and the scanner apart`() {
        val to = mset().toTO()

        assertEquals("4711", to.supplierId)
        assertEquals(12, to.scannerId)
    }

    /**
     * A blank supplier is kept as it is rather than dropped, so what was recorded is what is read back.
     *
     * No such case for the scanner, which is an id and so has nothing between empty and set.
     */
    @Test
    fun `carries a blank supplier as it is`() {
        val original = mset().also { it.supplierId = "  " }

        assertEquals("  ", original.toTO().toEntity().supplierId)
    }
}
