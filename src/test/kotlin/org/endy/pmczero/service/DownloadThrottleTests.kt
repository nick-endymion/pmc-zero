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
 * Unit tests for [DownloadThrottle], which is the only thing between two downloads.
 *
 * Nothing is downloaded here, so these are about time alone: how long a call to [DownloadThrottle.awaitTurn]
 * takes, and what the next one after it is allowed to do. Every case is therefore about an elapsed
 * time, which is the part of this that a change can get wrong without anything else noticing.
 */
class DownloadThrottleTests {

    @BeforeEach
    fun setUp() {
        DownloadThrottle.reset()
    }

    @AfterEach
    fun tearDown() {
        // the throttle is process wide, so a case that left it on would hold back every test that runs
        // after this class
        DownloadThrottle.reset()
    }

    /**
     * The default, and what an application that configures nothing gets. Asserted with a handful of
     * calls rather than one, since a throttle that only lets the first one through would still pass
     * against a single call.
     */
    @Test
    fun `does not wait when no throttle is configured`() {
        DownloadThrottle.seconds = 0

        val elapsed = millisOf {
            repeat(10) { DownloadThrottle.awaitTurn() }
        }

        assertTrue(elapsed < 500, "ten calls without a throttle took ${elapsed}ms")
    }

    /** A negative value is nonsense rather than a request for a shorter gap, so it means no throttle. */
    @Test
    fun `does not wait when the configured throttle is negative`() {
        DownloadThrottle.seconds = -5

        val elapsed = millisOf {
            repeat(3) { DownloadThrottle.awaitTurn() }
        }

        assertTrue(elapsed < 500, "three calls with a negative throttle took ${elapsed}ms")
    }

    /**
     * The interval is counted from the start of the download that ran last, not from the end of it, so
     * a download that took longer than the interval does not make the next one wait again.
     *
     * The whole point of it: over a slow line a gallery would otherwise be paced by the line as well,
     * and a caller who sets a two second throttle for a host that answers in five would wait seven.
     */
    @Test
    fun `counts from the start of the last download rather than from its end`() {
        DownloadThrottle.seconds = 1
        DownloadThrottle.awaitTurn()

        // what a download that is slower than the throttle leaves behind: the time it took
        Thread.sleep(1_200)

        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "a download that took 1.2s still cost a wait of ${elapsed}ms")
    }

    /** Nothing to be late for, so the first download of a run goes out at once. */
    @Test
    fun `does not make the first download wait`() {
        DownloadThrottle.seconds = 2

        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertTrue(elapsed < 500, "the first download waited ${elapsed}ms")
    }

    /** The interval itself, which is the one number this class exists for. */
    @Test
    fun `keeps two downloads the configured seconds apart`() {
        DownloadThrottle.seconds = 1

        val elapsed = millisOf {
            DownloadThrottle.awaitTurn()
            DownloadThrottle.awaitTurn()
        }

        assertTrue(elapsed >= 1_000, "the second download started after ${elapsed}ms rather than after 1s")
    }

    /**
     * Across runs, which is why this is a process wide object and not a field of a worker: a scan
     * started right after the last file of the previous one was fetched waits out the rest of the
     * interval rather than starting flat out again.
     *
     * Two callers of the throttle are what two runs amount to, since every run brings a
     * [org.endy.pmczero.model.scraper.FileDownloader] of its own and shares nothing but this.
     */
    @Test
    fun `keeps the downloads of one caller apart from those of the next`() {
        DownloadThrottle.seconds = 1

        DownloadThrottle.awaitTurn()

        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertTrue(elapsed >= 1_000, "the next run waited ${elapsed}ms rather than after 1s")
    }

    /**
     * Two scrapes at once, which two callers on two threads of the servlet container are. Without the
     * lock both of them would read the same moment and decide they are first, so the throttle would
     * hold for one download and not for two.
     *
     * The second thread is started a moment after the first so that it really does arrive during the
     * wait of the first rather than after it, which is the case the lock is there for.
     */
    @Test
    fun `keeps the downloads of two threads apart`() {
        DownloadThrottle.seconds = 1
        val starts = mutableListOf<Long>()
        val lock = Any()

        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit(Callable {
                DownloadThrottle.awaitTurn()
                // the moment the download may start, which is not the moment the thread asked
                synchronized(lock) { starts.add(System.nanoTime()) }
            })
            Thread.sleep(100)
            pool.submit(Callable {
                DownloadThrottle.awaitTurn()
                synchronized(lock) { starts.add(System.nanoTime()) }
            }).get(10, TimeUnit.SECONDS)
            first.get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        val apart = (starts.maxOrNull()!! - starts.minOrNull()!!) / 1_000_000
        assertEquals(2, starts.size, "both downloads went out")
        assertTrue(apart >= 1_000, "the two downloads started ${apart}ms apart rather than after 1s")
    }

    /**
     * A [DownloadThrottle.reset] is what a test between two cases needs, since the state is process
     * wide: without it the first download of the next case would wait for a file of this one.
     */
    @Test
    fun `forgets the last download on a reset`() {
        DownloadThrottle.seconds = 1
        DownloadThrottle.awaitTurn()
        DownloadThrottle.reset()

        val elapsed = millisOf { DownloadThrottle.awaitTurn() }

        assertEquals(0, DownloadThrottle.seconds, "a reset also switches the throttle off")
        assertTrue(elapsed < 500, "the download after a reset waited ${elapsed}ms")
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