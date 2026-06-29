package io.github.surajcm.bucketwatch;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that S3ChangeEvent types are idiomatic from Java:
 * plain constructors, standard getters, no Kotlin Companion access required.
 */
class S3ChangeEventJavaTest {

    private final Instant now = Instant.parse("2026-06-28T00:00:00Z");

    @Test
    void createdEventIsConstructibleAndReadableFromJava() {
        S3ChangeEvent.Created event = new S3ChangeEvent.Created(
                "my-bucket", "config/app.json", now, "abc123", 1024L
        );
        assertEquals("my-bucket", event.getBucket());
        assertEquals("config/app.json", event.getKey());
        assertEquals(now, event.getTimestamp());
        assertEquals("abc123", event.getEtag());
        assertEquals(1024L, event.getSizeBytes());
    }

    @Test
    void modifiedEventIsConstructibleAndReadableFromJava() {
        S3ChangeEvent.Modified event = new S3ChangeEvent.Modified(
                "b", "k", now, "prev-etag", "curr-etag"
        );
        assertEquals("prev-etag", event.getPreviousEtag());
        assertEquals("curr-etag", event.getCurrentEtag());
    }

    @Test
    void deletedEventIsConstructibleAndReadableFromJava() {
        S3ChangeEvent.Deleted event = new S3ChangeEvent.Deleted(
                "b", "k", now, "prev-etag"
        );
        assertEquals("prev-etag", event.getPreviousEtag());
    }

    @Test
    void basePropertiesAccessibleViaParentTypeFromJava() {
        S3ChangeEvent event = new S3ChangeEvent.Created("bucket", "key", now, "e", 0L);
        assertEquals("bucket", event.getBucket());
        assertEquals("key", event.getKey());
        assertEquals(now, event.getTimestamp());
    }

    @Test
    void instanceofChecksWorkFromJava() {
        S3ChangeEvent event = new S3ChangeEvent.Created("b", "k", now, "e", 0L);
        assertTrue(event instanceof S3ChangeEvent.Created);
        assertFalse(event instanceof S3ChangeEvent.Modified);
        assertFalse(event instanceof S3ChangeEvent.Deleted);
    }

    @Test
    void dataClassEqualityWorksFromJava() {
        S3ChangeEvent.Created e1 = new S3ChangeEvent.Created("b", "k", now, "e", 100L);
        S3ChangeEvent.Created e2 = new S3ChangeEvent.Created("b", "k", now, "e", 100L);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }
}
