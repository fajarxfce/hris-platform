package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import java.time.Instant

/** Safe retained reason and a bounded delay; raw failure parameters never enter the journal. */
data class CollaborationSpaceRoomResolution(
    val failure: CollaborationSpaceRoomFailure,
    val delaySeconds: Int?,
)

fun resolveCollaborationSpaceRoomFailure(
    lease: CollaborationSpaceRoomLease,
    failure: Failure,
    at: Instant,
): CollaborationSpaceRoomResolution {
    val reason =
        when {
            failure.kind in setOf(FailureKind.FORBIDDEN, FailureKind.UNAUTHENTICATED) ->
                CollaborationSpaceRoomFailure.ACCESS_DENIED
            failure.code in
                setOf(
                    "collaboration_room_mismatch",
                    "collaboration_room_recovery_requires_creator",
                ) -> CollaborationSpaceRoomFailure.ROOM_MISMATCH
            failure.code in
                setOf(
                    "collaboration_provider_invalid_response",
                    "collaboration_device_migration_required",
                    "collaboration_identity_migration_required",
                ) -> CollaborationSpaceRoomFailure.PROVIDER_IDENTITY_CHANGED
            failure.code in
                setOf(
                    "collaboration_provider_unavailable",
                    "collaboration_provider_busy",
                    "collaboration_provider_rejected",
                    "collaboration_provider_failed",
                ) -> CollaborationSpaceRoomFailure.PROVIDER_UNAVAILABLE
            else -> CollaborationSpaceRoomFailure.PUBLICATION_CHANGED
        }
    val transient =
        failure.code in
            setOf(
                "collaboration_provider_unavailable",
                "collaboration_provider_busy",
                "database_busy",
                "collaboration_admission_changed",
                "collaboration_space_request_busy",
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
    return CollaborationSpaceRoomResolution(reason, if (retry) delay else null)
}
