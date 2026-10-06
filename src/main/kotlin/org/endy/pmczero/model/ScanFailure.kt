package org.endy.pmczero.model

/**
 * One element of a scan that could not be handled, and why.
 *
 * A scan over the elements of a page runs into elements that cannot be handled all the time, and the
 * pipeline has nowhere to put that: [org.endy.pmczero.model.scraper.Worker.applya] answers nothing and
 * throws rather than return. So the outcome is collected on the [ScanningKontext] instead, which is
 * what lets the scan carry on past it, see
 * [org.endy.pmczero.model.scraper.RecoveryWorker].
 *
 * The reason is the message of the exception that element threw, which is what tells a caller whether a
 * retry is worth it: a 404 will fail the same way forever, whereas a timeout or a missing session may
 * well succeed next time.
 *
 * @param element the element that could not be handled, i.e. the url it was found under
 */
data class ScanFailure(
    val element: String,
    val reason: String
) {
    constructor(element: String, reason: Throwable) : this(element, reason.message ?: reason.toString())
}
