package org.endy.pmczero.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [PageThrottle], which is the only thing between two pages a scraper fetches.
 *
 * Nothing is fetched here, so these are about time alone: how long a call to [PageThrottle.awaitTurn]
 * takes, and what the next one after it is allowed to do. Every case is therefore about an elapsed time,
 * which is the part of this that a change can get wrong without anything else noticing.
 *
 * The mechanics are [Throttle] and are covered by [DownloadThrottleTests], which runs the same cases
 * against the throttle over files. What is added here is that this is a throttle of its own: it does
 * not hold back the downloads of a run, and a download throttle does not hold back its pages, which is
 * the whole reason there are two.
 */
class PageThrottleTests {

    @BeforeEach
    fun setUp() {
        PageThrottle.reset()
    }

    @AfterEach
    fun tearDown() {
        // the throttle is process wide, so a case that left it on would hold back every test that runs
        // after this class
        PageThrottle.reset()
    }

    /**
     * The default, and what an application that configures nothing gets. Asserted with a handful of calls
     * rather than one, since a throttle that only lets the first one through would still pass against a
     * single call.
     */
    @Test
    fun `does not wait when no throttle is configured`() {
        PageThrottle.seconds = 0

        val elapsed = millisOf {
            repeat(10) { PageThrottle.awaitTurn() }
        }

        assertTrue(elapsed < 500, "ten calls without a throttle took ${elapsed}ms")
    }

    /** A negative value is nonsense rather than a request for a shorter gap, so it means no throttle. */
    @Test
    fun `does not wait when the configured throttle is negative`() {
        PageThrottle.seconds = -5

        val elapsed = millisOf {
            repeat(3) { PageThrottle.awaitTurn() }
        }

        assertTrue(elapsed < 500, "three calls with a negative throttle took ${elapsed}ms")
    }

    /**
     * The interval is counted from the start of the page that was fetched last, not from the end of it,
     * so a page that took longer than the interval does not make the next one wait again.
     *
     * The whole point of it: over a slow line a gallery index would otherwise be paced by the line as
     * well, and a caller who sets a two second throttle for a host that answers in five would wait seven.
     */
    @Test
    fun `counts from the start of the last page rather than from its end`() {
        PageThrottle.seconds = 1
        PageThrottle.awaitTurn()

        // what a page that is slower than the throttle leaves behind: the time it took
        Thread.sleep(1_200)

        val elapsed = millisOf { PageThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "a page that took 1.2s still cost a wait of ${elapsed}ms")
    }

    /** Nothing to be late for, so the first page of a run goes out at once. */
    @Test
    fun `does not make the first page wait`() {
        PageThrottle.seconds = 2

        val elapsed = millisOf { PageThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "the first page waited ${elapsed}ms")
    }

    /** The interval itself, which is the one number this class exists for. */
    @Test
    fun `keeps two pages the configured seconds apart`() {
        PageThrottle.seconds = 1

        val elapsed = millisOf {
            PageThrottle.awaitTurn()
            PageThrottle.awaitTurn()
        }

        assertTrue(elapsed >= 1_000, "the second page was fetched after ${elapsed}ms rather than after 1s")
    }

    /**
     * Across runs, which is why this is a process wide object and not a field of a worker: a scan
     * started right after the last page of the previous one was read waits out the rest of the interval
     * rather than starting flat out again.
     *
     * Two callers of the throttle are what two runs amount to, since every run brings a
     * [org.endy.pmczero.model.scraper.StructuredWorker] of its own and shares nothing but this.
     */
    @Test
    fun `keeps the pages of one caller apart from those of the next`() {
        PageThrottle.seconds = 1

        PageThrottle.awaitTurn()

        val elapsed = millisOf { PageThrottle.awaitTurn() }

        assertTrue(elapsed >= 1_000, "the next run waited ${elapsed}ms rather than after 1s")
    }

    /**
     * Two scrapes at once, which two callers on two threads of the servlet container are. Without the
     * lock both of them would read the same moment and decide they are first, so the throttle would hold
     * for one page and not for two.
     *
     * The second thread is started a moment after the first so that it really does arrive during the
     * wait of the first rather than after it, which is the case the lock is there for.
     */
    @Test
    fun `keeps the pages of two threads apart`() {
        PageThrottle.seconds = 1
        val starts = mutableListOf<Long>()
        val lock = Any()

        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable {
                PageThrottle.awaitTurn()
                // the moment the page may be fetched, which is not the moment the thread asked
                synchronized(lock) { starts.add(System.nanoTime()) }
            })
            Thread.sleep(100)
            pool.submit(Callable {
                PageThrottle.awaitTurn()
                synchronized(lock) { starts.add(System.nanoTime()) }
            }).get(10, TimeUnit.SECONDS)
            first.get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        val apart = (starts.maxOrNull()!! - starts.minOrNull()!!) / 1_000_000
        assertEquals(2, starts.size, "both pages went out")
        assertTrue(apart >= 1_000, "the two pages were fetched ${apart}ms apart rather than after 1s")
    }

    /**
     * A [PageThrottle.reset] is what a test between two cases needs, since the state is process wide:
     * without it the first page of the next case would wait for one of this.
     */
    @Test
    fun `forgets the last page on a reset`() {
        PageThrottle.seconds = 1
        PageThrottle.awaitTurn()
        PageThrottle.reset()

        val elapsed = millisOf { PageThrottle.awaitTurn() }

        assertEquals(0, PageThrottle.seconds, "a reset also switches the throttle off")
        assertTrue(elapsed < 500, "the page after a reset waited ${elapsed}ms")
    }

    // -------------------------------------------------------------------------------------
    // Two throttles, not one
    // -------------------------------------------------------------------------------------

    /**
     * The reason this is a second throttle: a deployment that holds its pages back does not thereby hold
     * its files back, and the other way round.
     */
    @Test
    fun `holds the downloads back without holding the pages back`() {
        DownloadThrottle.seconds = 5

        val elapsed = millisOf { PageThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "a download throttle made a page wait ${elapsed}ms")
    }

    /** And the other way round, which is the direction that matters for an import of files. */
    @Test
    fun `holds the pages back without holding the downloads back`() {
        PageThrottle.seconds = 5

        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "a page throttle made a download wait ${elapsed}ms")
    }

    /** A page fetched is not a file downloaded, so the two do not queue up behind one another. */
    @Test
    fun `a page does not make the next download wait`() {
        PageThrottle.seconds = 5
        DownloadThrottle.seconds = 5

        PageThrottle.awaitTurn()
        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "the download after a page waited ${elapsed}ms")
    }

    /** One pace per kind of request, so a run that does both paces each of them. */
    @Test
    fun `pacing the pages does not pace the downloads of the same run`() {
        PageThrottle.seconds = 1
        DownloadThrottle.seconds = 1

        PageThrottle.awaitTurn()
        val downloads = millisOf {
            DownloadThrottle.awaitTurn()
            DownloadThrottle.awaitTurn()
        }

        assertTrue(downloads >= 1_000, "the two downloads were ${downloads}ms apart rather than after 1s")
        assertEquals(0L, millisOf { PageThrottle.awaitTurn() }, "the page after it had nothing to wait for")
    }

    /** Resetting one leaves the other as it was, since they do not share a state. */
    @Test
    fun `a reset of the pages leaves the downloads alone`() {
        DownloadThrottle.seconds = 5
        PageThrottle.seconds = 5
        PageThrottle.reset()

        assertEquals(5, DownloadThrottle.seconds)
        assertEquals(0, PageThrottle.seconds)
    }

    /**
     * The wall clock of how long [block] took, so a case reads as "this took at least a second" rather
     * than as the arithmetic around two [System.nanoTime] readings.
     */
    private inline fun millisOf(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}