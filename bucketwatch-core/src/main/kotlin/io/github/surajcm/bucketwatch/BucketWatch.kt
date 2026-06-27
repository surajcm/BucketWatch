package io.github.surajcm.bucketwatch

import software.amazon.awssdk.services.s3.S3Client

/**
 * Placeholder entry point for the BucketWatch library.
 *
 * This exists only to prove the toolchain compiles against the AWS SDK v2 (the
 * [S3Client] reference below). The real builder, lifecycle, and watch logic land in
 * later milestones — see `implementation-plan.md` (M1 onward).
 */
public object BucketWatch {

    /** Maven artifact name for this module. */
    public const val NAME: String = "bucketwatch-core"

    /**
     * No-op factory stub. Confirms the AWS SDK [S3Client] type is on the public
     * compile classpath; replaced by the real builder in M4.
     */
    public fun create(s3Client: S3Client): Nothing =
        throw NotImplementedError("BucketWatch is not implemented yet (see implementation-plan.md, M4).")
}
