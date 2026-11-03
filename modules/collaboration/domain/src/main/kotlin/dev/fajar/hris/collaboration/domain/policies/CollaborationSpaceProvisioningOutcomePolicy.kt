package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import java.time.Instant

/** Safe retained reason and a bounded delay; raw failure parameters never enter the journal. */
data class CollaborationSpaceProvisioningResolution(
    val failure: CollaborationSpaceProvisioningFailure,
    val delaySeconds: Int?,
)

fun resolveCollaborationSpaceProvisioningFailure(
    lease: CollaborationSpaceProvisioningLease,
    failure: Failure,
    at: Instant,
): CollaborationSpaceProvisioningResolution {
    val reason =
        when {
            failure.kind in setOf(FailureKind.FORBIDDEN, FailureKind.UNAUTHENTICATED) ->
                CollaborationSpaceProvisioningFailure.ACCESS_DENIED
            failure.code in
                setOf(
                    "collaboration_provider_invalid_response",
                    "collaboration_device_migration_required",
                    "collaboration_identity_migration_required",
                ) -> CollaborationSpaceProvisioningFailure.PROVIDER_IDENTITY_CHANGED
            failure.code in
                setOf(
                    "collaboration_provider_unavailable",
                    "collaboration_provider_busy",
                    "collaboration_provider_rejected",
                    "collaboration_provider_failed",
                ) -> CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE
            else -> CollaborationSpaceProvisioningFailure.PUBLICATION_CHANGED
        }
    val transient =
        failure.code in
            setOf(
                "collaboration_provider_unavailable",
                "collaboration_provider_busy",
                "database_busy",
                "collaboration_admission_changed",
            ) &&
            failure.kind in
                setOf(FailureKind.UNAVAILABLE, FailureKind.RATE_LIMITED, FailureKind.CONFLICT)
    val base = 1 shl (lease.attempt - 1).coerceIn(0, 5)
    val delay = (base + Math.floorMod(lease.token.leastSignificantBits, 3)).coerceAtMost(30)
    val retry =
        transient &&
            lease.attempt in 1..7 &&
            at >= lease.request.requestedAt &&
            at < lease.until &&
            at.plusSeconds(delay.toLong() + 1) < lease.request.expiresAt
    return CollaborationSpaceProvisioningResolution(reason, if (retry) delay else null)
}
