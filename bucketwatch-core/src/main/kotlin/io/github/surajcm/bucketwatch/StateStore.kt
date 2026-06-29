package io.github.surajcm.bucketwatch

import java.time.Instant

/**
 * Snapshot of a single S3 object's metadata, tracked between polls to detect changes.
 *
 * @property etag ETag as returned by S3; changes on every content update.
 * @property sizeBytes Object size in bytes.
 * @property lastModified Last-modified time as reported by S3.
 */
public data class ObjectState(
    public val etag: String,
    public val sizeBytes: Long,
    public val lastModified: Instant,
)

/**
 * Pluggable store that tracks [ObjectState] per S3 key between poll cycles.
 *
 * Implementations must be thread-safe — the change-detection engine reads and
 * writes state concurrently with scheduled polls.
 *
 * The default implementation is [io.github.surajcm.bucketwatch.internal.InMemoryStateStore].
 * Provide a custom implementation via the builder to persist state across restarts.
 */
public interface StateStore {

    /** Returns the stored state for [key], or `null` if the key is not tracked. */
    public fun get(key: String): ObjectState?

    /** Stores or replaces the state associated with [key]. */
    public fun put(key: String, state: ObjectState)

    /**
     * Removes the state for [key] and returns it, or returns `null` if the key was not tracked.
     */
    public fun remove(key: String): ObjectState?

    /** Returns a snapshot of all currently tracked keys. */
    public fun getAllKeys(): Set<String>

    /** Removes all tracked state. */
    public fun clear()
}
