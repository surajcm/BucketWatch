package io.github.surajcm.bucketwatch

import io.github.surajcm.bucketwatch.internal.InMemoryStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryStateStoreTest {

    private lateinit var store: StateStore
    private val now = Instant.parse("2026-06-28T00:00:00Z")

    @BeforeEach
    fun setUp() {
        store = InMemoryStateStore()
    }

    private fun state(etag: String = "etag", sizeBytes: Long = 100L) =
        ObjectState(etag = etag, sizeBytes = sizeBytes, lastModified = now)

    @Test
    fun `get returns null for unknown key`() {
        assertNull(store.get("missing"))
    }

    @Test
    fun `put then get round-trips correctly`() {
        val s = state()
        store.put("k", s)
        assertEquals(s, store.get("k"))
    }

    @Test
    fun `put overwrites existing entry`() {
        store.put("k", state(etag = "v1"))
        store.put("k", state(etag = "v2"))
        assertEquals("v2", store.get("k")!!.etag)
    }

    @Test
    fun `remove returns removed state and key is gone`() {
        store.put("k", state())
        val removed = store.remove("k")
        assertNotNull(removed)
        assertNull(store.get("k"))
    }

    @Test
    fun `remove returns null for unknown key`() {
        assertNull(store.remove("missing"))
    }

    @Test
    fun `getAllKeys returns all stored keys`() {
        store.put("a", state())
        store.put("b", state())
        assertEquals(setOf("a", "b"), store.getAllKeys())
    }

    @Test
    fun `getAllKeys returns empty set when store is empty`() {
        assertTrue(store.getAllKeys().isEmpty())
    }

    @Test
    fun `clear removes all entries`() {
        store.put("a", state())
        store.put("b", state())
        store.clear()
        assertTrue(store.getAllKeys().isEmpty())
    }

    @Test
    fun `getAllKeys snapshot is stable while store mutates`() {
        store.put("a", state())
        store.put("b", state())
        val snapshot = store.getAllKeys()
        store.put("c", state())
        assertEquals(setOf("a", "b"), snapshot) // snapshot not affected by later put
    }

    @Test
    fun `concurrent puts from multiple threads do not lose entries`() {
        val threadCount = 50
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val futures = (1..threadCount).map { i ->
                executor.submit { latch.await(); store.put("key-$i", state(etag = "etag-$i")) }
            }
            latch.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
        assertEquals(threadCount, store.getAllKeys().size)
    }

    @Test
    fun `concurrent reads and writes do not throw`() {
        repeat(100) { store.put("seed-$it", state()) }
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(10)
        try {
            val futures = (1..30).map { i ->
                executor.submit {
                    latch.await()
                    if (i % 2 == 0) store.put("k-$i", state()) else store.get("seed-${i % 100}")
                }
            }
            latch.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
    }
}
