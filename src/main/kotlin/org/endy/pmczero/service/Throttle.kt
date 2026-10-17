package org.endy.pmczero.service

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * How long this application waits between two requests of the same kind.
 *
 * The mechanics of one pace, shared by the two this application paces,
 * [DownloadThrottle] over the files of a scrape and [PageThrottle] over the pages a scraper fetches for
 * itself. What they wait for is different and what they are for is different, so that much is written on
 * each of them; what they do while waiting is not, and that is this.
 *
 * One pace per instance and nothing shared between two of them: an instance that is paced over files
 * does not know of one that is paced over pages, so a deployment can hold its pages back without
 * slowing its pictures down, and the other way round. They are not one throttle with two settings,
 * because they are not two settings of the same thing.
 *
 * ### What it counts
 *
 * The gap between the *start* of one request and the start of the next, which is what keeps a slow one
 * from adding its own time to every request behind it: a run over a fast line keeps its pace, and one
 * over a slow line is throttled by the line as well. Waiting before the request rather than after it is
 * what makes that possible, since a wait after the answer is here slows the caller down without slowing
 * the host at all.
 *
 * ### Across runs, scans and threads
 *
 * An instance that outlives the run, which is what "at least this many seconds after the last one
 * began" means once more than one scrape is going on:
 *
 * - **across runs**, which is the point of it. A scrape started right after another one ended still
 *   waits for whatever is left of the interval, so a caller who starts five runs in a row does not
 *   hit a host five times over flat out. A wait that lived in the run would let every run start as
 *   fast as the previous one ended.
 * - **across scans in one run**, since a set of several pages is fetched through the same workers.
 * - **across threads**, since two callers can scrape at the same time. The lock is held for the
 *   length of a wait, so two requests that ask in the same moment do not both decide they are first:
 *   one of them is a second late by the time the other is let go.
 *
 * ### Turning it off
 *
 * [seconds] is 0, which is what an application that says nothing gets, and then [awaitTurn] returns at
 * once and records nothing: a call that arrives while no throttle is configured neither waits nor makes
 * the next configured one wait for it.
 *
 * @param subject what is being paced, e.g. `download`, so that a message about a wait says which of
 * the two it was rather than naming neither
 */
class Throttle(private val subject: String) {

    private val logger = LoggerFactory.getLogger(Throttle::class.java)

    /**
     * Guards [lastStartedAt] and is held for the length of a wait.
     *
     * Held over the wait rather than released while sleeping, which is what serializes the two callers
     * that ask at the same instant: each of them leaves with a [lastStartedAt] that the other then has
     * to wait for, instead of both finding the interval already over.
     */
    private val gate = ReentrantLock()

    /**
     * How many seconds have to pass between the start of one request and the start of the next.
     *
     * 0, or anything below it, switches the throttle off, which is the default: an application that does
     * not configure a throttle paces nothing, which is what every deployment did before there was
     * anything to configure. In seconds rather than in milliseconds because the wait is of the order of
     * a second or more and a sub second value would only ever be a test.
     *
     * Writable rather than a constructor argument, since a pace is reached through a static rather than
     * injected, see [org.endy.pmczero.configuration.DownloadThrottleConfig]. Volatile so that a change
     * reaches the threads that are waiting on it.
     */
    @Volatile
    var seconds: Long = 0

    /**
     * When the request that ran last was started, on the monotonic clock of [System.nanoTime], or 0 when
     * nothing has been requested since the last [reset].
     *
     * The monotonic clock rather than [System.currentTimeMillis] because it is the only one that does
     * not jump when the clock of the machine is set, which would otherwise either wave a wait through
     * or hold one for an hour.
     */
    private var lastStartedAt: Long = 0

    /**
     * Waits until a request of this kind may start, and records that one is starting.
     *
     * Called before the request rather than after it, so the gap is between two starts, and called by
     * the caller that is about to make it rather than inside a fetcher, so it counts what that caller
     * paces and nothing else.
     *
     * Returns without waiting when no throttle is configured, which is every call of an application
     * that never set one.
     */
    fun awaitTurn() {
        val interval = seconds
        // nothing to wait for and nothing to remember: a request that happened while no throttle was
        // configured must not make the first configured one wait for it
        if (interval <= 0) return

        gate.withLock {
            val now = System.nanoTime()
            // nothing requested since the last reset, or the interval is already over: start now
            val waitNanos = lastStartedAt + TimeUnit.SECONDS.toNanos(interval) - now
            if (lastStartedAt == 0L || waitNanos <= 0) {
                lastStartedAt = now
                return
            }

            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos)
            } catch (e: InterruptedException) {
                // the flag back on, since whoever interrupted this thread still wants to know, and the
                // request goes ahead rather than fails over a wait. A scrape is not the place to decide
                // what an interruption of it means, and the one calling it is the only party that could
                Thread.currentThread().interrupt()
                logger.debug("the wait before a {} was cut short: {}", subject, e.message)
            }

            // the moment the request goes out, not the one it was decided at, since a wait that was cut
            // short and one that ran its course both start the request now
            lastStartedAt = System.nanoTime()
        }
    }

    /**
     * Forgets the request that ran last and switches the throttle off.
     *
     * For tests, and nothing else has a reason to: the state of one throttle is process wide, so a test
     * that left it on would hold back every test that runs after it, and one that left a recorded
     * request behind would make the first real request of the next test wait for something that no
     * longer exists.
     */
    fun reset() {
        seconds = 0
        gate.withLock { lastStartedAt = 0 }
    }
}