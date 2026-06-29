package io.github.surajcm.bucketwatch

import java.time.Instant

/**
 * Represents a change detected in an S3 bucket during a poll cycle.
 *
 * Presence determines [Created]/[Deleted]; ETag comparison determines [Modified].
 * Rapid changes between polls may collapse into a single event — this is an accepted
 * constraint of the polling model.
 */
public sealed class S3ChangeEvent {

    /** S3 bucket in which the change was detected. */
    public abstract val bucket: String

    /** S3 object key that changed. */
    public abstract val key: String

    /** Wall-clock time at which the change was detected (not the S3 object's last-modified time). */
    public abstract val timestamp: Instant

    /**
     * An object that did not exist in the previous poll was present in the current poll.
     *
     * @property etag ETag of the newly created object.
     * @property sizeBytes Size of the newly created object in bytes.
     */
    public data class Created(
        override val bucket: String,
        override val key: String,
        override val timestamp: Instant,
        public val etag: String,
        public val sizeBytes: Long,
    ) : S3ChangeEvent()

    /**
     * An object existed in the previous poll and still exists, but its ETag has changed.
     *
     * @property previousEtag ETag observed in the previous poll.
     * @property currentEtag ETag observed in the current poll.
     */
    public data class Modified(
        override val bucket: String,
        override val key: String,
        override val timestamp: Instant,
        public val previousEtag: String,
        public val currentEtag: String,
    ) : S3ChangeEvent()

    /**
     * An object that existed in the previous poll was absent in the current poll.
     *
     * @property previousEtag ETag observed in the last poll before deletion.
     */
    public data class Deleted(
        override val bucket: String,
        override val key: String,
        override val timestamp: Instant,
        public val previousEtag: String,
    ) : S3ChangeEvent()
}
