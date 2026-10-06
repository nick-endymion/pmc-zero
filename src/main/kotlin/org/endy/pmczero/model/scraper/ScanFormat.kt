package org.endy.pmczero.model.scraper

import kotlinx.serialization.json.Json

/**
 * The json a [Scraper] is written to and read back from, in one place.
 *
 * A scraper is configuration rather than code: it is stored as text in `a.scanner.serialization`, sent
 * to an endpoint as a request parameter and handed to a scan that runs it. So whatever writes one has
 * to be able to read it again with the same set of rules, or a scraper saved by one release comes back
 * as something else, or not at all, after another. Hence one [Json] rather than one per caller.
 *
 * No `serializersModule` here, and deliberately so. [Parser] and [Worker] are both sealed, so
 * kotlinx.serialization resolves their subclasses on its own and writes each one under its
 * `@SerialName`, which is what makes `"type":"dom"` and `"type":"fileDownloader"` work in a stored
 * scraper without a registry. An open (abstract) base would instead need every subclass registered by
 * hand, and a [Json] that carries no such registry would fail on the first scraper it met.
 */
object ScanFormat {

    /**
     * Pretty printed, since a scraper is written by hand and read by a person as often as by the
     * application. The whitespace is not what distinguishes it from a stored one, so it makes no
     * difference whether the json came pretty printed or compact.
     */
    val json: Json = Json { prettyPrint = true }
}
