package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.resolveCollaborationSpaceProvisioningFailure
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceProvisioningOutcomePolicyTest {
    private val at = Instant.parse("2026-11-03T22:00:00Z")
    private val lease =
        CollaborationSpaceProvisioningLease(
            CollaborationSpaceProvisioningRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                0,
                0,
                at.minusSeconds(60),
                null,
                UUID.randomUUID(),
                ClientVersion(ClientPlatform.ANDROID, 28),
                at,
                at.plusSeconds(60),
            ),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            at.plusSeconds(30),
        )

    @Test
    fun waitingForOriginalRuntimeUsesTheSameFiniteProofAndAttemptBudgets() {
        val busy = Failure(FailureKind.UNAVAILABLE, "collaboration_runtime_busy")
        val result = resolveCollaborationSpaceProvisioningFailure(lease, busy, at)
        assertEquals(CollaborationSpaceProvisioningFailure.PUBLICATION_CHANGED, result.failure)
        assertTrue(requireNotNull(result.delaySeconds) in 1..3)
        for (attempt in listOf(0, 8, 9)) assertNull(
            resolveCollaborationSpaceProvisioningFailure(lease.copy(attempt = attempt), busy, at)
                .delaySeconds
        )
        assertNull(
            resolveCollaborationSpaceProvisioningFailure(lease, busy, lease.until).delaySeconds
        )
        assertNull(
            resolveCollaborationSpaceProvisioningFailure(
                    lease.copy(request = lease.request.copy(expiresAt = at.plusSeconds(1))),
                    busy,
                    at,
                )
                .delaySeconds
        )
        assertNull(
            resolveCollaborationSpaceProvisioningFailure(
                    lease,
                    busy.copy(kind = FailureKind.FORBIDDEN),
                    at,
                )
                .delaySeconds
        )
        assertNull(
            resolveCollaborationSpaceProvisioningFailure(
                    lease,
                    busy.copy(code = "collaboration_runtime_changed"),
                    at,
                )
                .delaySeconds
        )
    }

    @Test
    fun temporaryFailureUsesFiniteAttemptAndDeadlineBudgetsAndIgnoresRawParameters() {
        val failure = Failure(FailureKind.UNAVAILABLE, "collaboration_provider_unavailable")
        for (attempt in 1..7) {
            val current = lease.copy(attempt = attempt)
            val planned = resolveCollaborationSpaceProvisioningFailure(current, failure, at)
            assertEquals(
                CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE,
                planned.failure,
            )
            assertTrue(requireNotNull(planned.delaySeconds) in 1..30)
            assertEquals(
                planned,
                resolveCollaborationSpaceProvisioningFailure(
                    current,
                    failure.copy(
                        parameters =
                            mapOf(
                                "retryAfterSeconds" to "999999999",
                                "token" to "fixture-not-for-journal",
                            )
                    ),
                    at,
                ),
            )
        }
        for (attempt in listOf(0, 8, 9, Int.MAX_VALUE, Int.MIN_VALUE)) assertNull(
            resolveCollaborationSpaceProvisioningFailure(lease.copy(attempt = attempt), failure, at)
                .delaySeconds
        )
        for (time in listOf(at.minusNanos(1), lease.until, lease.until.plusSeconds(1))) assertNull(
            resolveCollaborationSpaceProvisioningFailure(lease, failure, time).delaySeconds
        )
        val nearlyExpired =
            lease.copy(
                request = lease.request.copy(expiresAt = at.plusSeconds(1)),
                until = at.plusSeconds(1),
            )
        assertNull(
            resolveCollaborationSpaceProvisioningFailure(nearlyExpired, failure, at).delaySeconds
        )
    }

    @Test
    fun accessAndIdentityFailuresEndImmediatelyAndUnknownErrorsDoNotLoop() {
        for (failure in
            listOf(
                Failure(FailureKind.FORBIDDEN, "collaboration_provider_busy"),
                Failure(FailureKind.UNAUTHENTICATED, "session_revoked"),
            )) {
            val planned = resolveCollaborationSpaceProvisioningFailure(lease, failure, at)
            assertEquals(CollaborationSpaceProvisioningFailure.ACCESS_DENIED, planned.failure)
            assertNull(planned.delaySeconds)
        }
        for (code in
            listOf(
                "collaboration_provider_invalid_response",
                "collaboration_device_migration_required",
                "collaboration_identity_migration_required",
            )) {
            val planned =
                resolveCollaborationSpaceProvisioningFailure(
                    lease,
                    Failure(FailureKind.UNAVAILABLE, code),
                    at,
                )
            assertEquals(
                CollaborationSpaceProvisioningFailure.PROVIDER_IDENTITY_CHANGED,
                planned.failure,
            )
            assertNull(planned.delaySeconds)
        }
        for (failure in
            listOf(
                Failure(FailureKind.UNAVAILABLE, "unknown_error"),
                Failure(FailureKind.UNEXPECTED, "collaboration_provider_unavailable"),
                Failure(FailureKind.UNAVAILABLE, "collaboration_provider_rejected"),
            )) assertNull(
            resolveCollaborationSpaceProvisioningFailure(lease, failure, at).delaySeconds
        )
    }
}
