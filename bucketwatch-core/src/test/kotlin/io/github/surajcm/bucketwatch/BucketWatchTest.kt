package io.github.surajcm.bucketwatch

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client

/**
 * Smoke test proving the Kotlin + JUnit 5 + AWS SDK toolchain is wired correctly.
 * Real behaviour is covered by tests added in later milestones.
 */
class BucketWatchTest {

    @Test
    fun `exposes the module name`() {
        assertEquals("bucketwatch-core", BucketWatch.NAME)
    }

    @Test
    fun `create is not implemented yet`() {
        // A mock keeps the smoke test free of AWS credentials; the stub throws
        // NotImplementedError before it ever touches the client.
        val s3Client = mockk<S3Client>()
        assertThrows(NotImplementedError::class.java) {
            BucketWatch.create(s3Client)
        }
    }
}
