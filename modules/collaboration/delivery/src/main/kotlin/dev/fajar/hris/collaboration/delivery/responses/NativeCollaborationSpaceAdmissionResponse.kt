package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceAdmission
import java.time.Instant
import java.util.UUID

/** Discovery metadata only. Every subsequent operation acquires its own current admission. */
data class NativeCollaborationSpaceAdmissionResponse(
    val spaceId: UUID,
    val accountId: UUID,
    val nativeSessionId: UUID,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val admittedAt: Instant,
    val expiresAt: Instant,
) {
    constructor(
        value: CollaborationSpaceAdmission
    ) : this(
        value.spaceId,
        value.accountId,
        value.nativeSessionId,
        value.nativeSessionVersion,
        value.credentialVersion,
        value.admittedAt,
        value.expiresAt,
    )
}
