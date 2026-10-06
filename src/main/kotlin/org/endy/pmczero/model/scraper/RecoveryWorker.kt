package org.endy.pmczero.model.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.endy.pmczero.model.ScanFailure
import org.endy.pmczero.model.ScanningKontext

/**
 * Hands each element to [worker] on its own and records the ones that fail, rather than letting the
 * first failure end the scan.
 *
 * A [Worker] either completes or throws, and in a pipeline over the elements of a page that is a
 * problem: one image that 404s would take the whole scan down, so every image that did download is lost
 * and the reason for the one that did not is gone with the stack trace. A gallery of two hundred images
 * routinely holds a handful that are unreachable, which is what makes failing the whole call useless
 * for exactly the pages this is meant for. So the outcome per element is collected and the scan runs
 * on.
 *
 * The failures land in [ScanningKontext.failures], which is where a caller reads them to decide
 * whether a retry is worth it. Nothing is rethrown: a single unreachable file is one entry of a list,
 * not a failure of the call.
 *
 * The worker being wrapped still has to be recorded by something else, since this only decides what
 * happens when it fails, not what it does when it succeeds.
 *
 * @param worker the worker to attempt each element with
 */
@Serializable
@SerialName("recovery")
class RecoveryWorker(val worker: Worker) : Worker() {

    override fun applya(element: String, scanningKontext: ScanningKontext) {
        try {
            worker.applya(element, scanningKontext)
        } catch (e: Throwable) {
            // Throwable rather than Exception on purpose: NotAccessibleException and NotFoundException
            // both extend Throwable directly, so catching Exception would let exactly the failures this
            // worker exists for abort the scan. Nothing is rethrown, because one element that cannot be
            // handled is not a failure of the scan.
            scanningKontext.failures.add(ScanFailure(element, e.message ?: e.toString()))
        }
    }
}
