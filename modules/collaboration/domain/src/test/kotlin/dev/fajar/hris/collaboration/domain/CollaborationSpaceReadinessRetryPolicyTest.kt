package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceReadinessRetryPolicyTest {
    private val now = Instant.parse("2026-11-09T19:00:00Z")
    private val checkpoint =
        CollaborationSpaceReadinessCheckpoint(
            CollaborationSpaceReadinessLease(
                UUID.randomUUID(),
                CollaborationSpaceReadinessChannel.MATRIX,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                now.plusSeconds(60),
            ),
            1,
            now.minusSeconds(10),
            now.minusSeconds(5),
            now.minusSeconds(10).plusSeconds(86400),
            now.minusSeconds(5),
            1,
            null,
        )

    @Test
    fun retryIsFiniteAndProgressRestartsTheBudgetWithoutExtendingPublicationLifetime() {
        assertTrue(validCollaborationSpaceReadinessCheckpoint(checkpoint))
        assertEquals(now.plusSeconds(2), collaborationSpaceReadinessRetryAt(checkpoint, null, now))
        assertEquals(
            now.plusSeconds(128),
            collaborationSpaceReadinessRetryAt(checkpoint.copy(attempts = 7), null, now),
        )
        assertNull(collaborationSpaceReadinessRetryAt(checkpoint.copy(attempts = 8), null, now))
        val progressed =
            checkpoint.copy(
                attempts = 0,
                after = CollaborationReadinessPosition(UUID.randomUUID(), UUID.randomUUID()),
            )
        assertEquals(now.plusSeconds(2), collaborationSpaceReadinessRetryAt(progressed, null, now))
        assertNull(
            collaborationSpaceReadinessRetryAt(
                checkpoint,
                null,
                checkpoint.expiresAt.minusSeconds(2),
            )
        )
        assertNull(collaborationSpaceReadinessRetryAt(checkpoint, null, checkpoint.expiresAt))
        assertNull(
            collaborationSpaceReadinessRetryAt(
                checkpoint,
                null,
                checkpoint.capturedAt.minusNanos(1),
            )
        )
        assertNull(collaborationSpaceReadinessRetryAt(checkpoint.copy(attempts = -1), null, now))
    }

    @Test
    fun providerBackoffCannotMakeAnImmediateLoopOrAnUnboundedDelay() {
        for ((request, seconds) in
            listOf(
                "-100" to 2L,
                "0" to 2L,
                "37" to 37L,
                "999999" to 300L,
                "broken" to 2L,
                "9999999999999999999999" to 2L,
            )) {
            val failure =
                Failure(
                    FailureKind.UNAVAILABLE,
                    "fixture_busy",
                    parameters = mapOf("retryAfterSeconds" to request),
                )
            assertEquals(
                now.plusSeconds(seconds),
                collaborationSpaceReadinessRetryAt(checkpoint, failure, now),
                request,
            )
        }
    }
}
