package org.endy.pmczero.service

import org.endy.pmczero.mapper.toEntity
import org.endy.pmczero.mapper.toTO
import org.endy.pmczero.model.modern.Medium
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the [Medium] mappers, over the fields that say how a medium is ordered.
 *
 * The mappers are written out field by field, so a field added to one side and forgotten on the other
 * is not a compile error: it is a value that reads back as null, see
 * [org.endy.pmczero.service.MsetMapperTests] for the same argument on the mappers of a set. The
 * ranking is the field most likely to be lost that way, since nothing on the way in writes it, so it
 * is checked in both directions.
 */
class MediumMapperTests {

    private fun medium() = Medium().apply {
        id = 5
        name = "doc.pdf"
        ranking = 3
    }

    /**
     * The ranking on the TO of a medium is what a caller reads to decide what order to show the media
     * of a set in, so it has to survive both directions.
     */
    @Test
    fun `a medium survives being mapped to a TO and back`() {
        val original = medium()

        val roundTripped = original.toTO().toEntity()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.ranking, roundTripped.ranking)
    }

    @Test
    fun `carries the ranking onto the TO`() {
        assertEquals(3, medium().toTO().ranking)
    }

    /**
     * A medium nobody ranked has none rather than a 0, so a caller can tell "not ranked" from
     * "ranked first" instead of having to agree on what 0 means.
     */
    @Test
    fun `carries a medium without a ranking`() {
        val original = medium().also { it.ranking = null }

        assertNull(original.toTO().ranking)
        assertNull(original.toTO().toEntity().ranking)
    }

    /**
     * Two media of one set may carry the same number, since the number is what the caller wrote down
     * and nothing here renumbers them.
     */
    @Test
    fun `carries a ranking that two media share`() {
        val first = medium().also { it.id = 5; it.ranking = 2 }
        val second = medium().also { it.id = 6; it.ranking = 2 }

        assertEquals(2, first.toTO().ranking)
        assertEquals(2, second.toTO().ranking)
    }

    /**
     * The number the caller wrote down is kept as it is, including a negative one, so a caller that
     * sorts backwards can say so rather than having it silently turned into a position from the top.
     */
    @Test
    fun `carries a negative ranking as it is`() {
        val original = medium().also { it.ranking = -1 }

        assertEquals(-1, original.toTO().toEntity().ranking)
    }
}