package io.github.surajcm.bucketwatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class S3ChangeEventTest {

    private val now = Instant.parse("2026-06-28T00:00:00Z")

    @Test
    fun `Created carries bucket key timestamp etag and sizeBytes`() {
        val event = S3ChangeEvent.Created(
            bucket = "my-bucket",
            key = "config/app.json",
            timestamp = now,
            etag = "abc123",
            sizeBytes = 1024L,
        )
        assertEquals("my-bucket", event.bucket)
        assertEquals("config/app.json", event.key)
        assertEquals(now, event.timestamp)
        assertEquals("abc123", event.etag)
        assertEquals(1024L, event.sizeBytes)
    }

    @Test
    fun `Modified carries previousEtag and currentEtag`() {
        val event = S3ChangeEvent.Modified(
            bucket = "b", key = "k", timestamp = now,
            previousEtag = "old", currentEtag = "new",
        )
        assertEquals("old", event.previousEtag)
        assertEquals("new", event.currentEtag)
    }

    @Test
    fun `Deleted carries previousEtag`() {
        val event = S3ChangeEvent.Deleted(
            bucket = "b", key = "k", timestamp = now,
            previousEtag = "old",
        )
        assertEquals("old", event.previousEtag)
    }

    @Test
    fun `data class equality holds for identical Created events`() {
        val e1 = S3ChangeEvent.Created("b", "k", now, "etag", 100L)
        val e2 = S3ChangeEvent.Created("b", "k", now, "etag", 100L)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun `data class equality distinguishes differing etags`() {
        val e1 = S3ChangeEvent.Created("b", "k", now, "etag1", 100L)
        val e2 = S3ChangeEvent.Created("b", "k", now, "etag2", 100L)
        assertNotEquals(e1, e2)
    }

    @Test
    fun `subtypes are S3ChangeEvent instances`() {
        val created: S3ChangeEvent = S3ChangeEvent.Created("b", "k", now, "e", 0L)
        val modified: S3ChangeEvent = S3ChangeEvent.Modified("b", "k", now, "prev", "curr")
        val deleted: S3ChangeEvent = S3ChangeEvent.Deleted("b", "k", now, "prev")

        assertTrue(created is S3ChangeEvent.Created)
        assertFalse(created is S3ChangeEvent.Modified)
        assertTrue(modified is S3ChangeEvent.Modified)
        assertTrue(deleted is S3ChangeEvent.Deleted)
    }

    @Test
    fun `when expression over sealed hierarchy is exhaustive`() {
        val event: S3ChangeEvent = S3ChangeEvent.Modified("b", "k", now, "p", "c")
        val label = when (event) {
            is S3ChangeEvent.Created -> "created"
            is S3ChangeEvent.Modified -> "modified"
            is S3ChangeEvent.Deleted -> "deleted"
        }
        assertEquals("modified", label)
    }

    @Test
    fun `base properties are accessible via sealed type reference`() {
        val event: S3ChangeEvent = S3ChangeEvent.Deleted("bucket-x", "key-y", now, "etag-z")
        assertEquals("bucket-x", event.bucket)
        assertEquals("key-y", event.key)
        assertEquals(now, event.timestamp)
    }
}
