package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRecipientDevice
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Exact public-key readiness; offline access expiry is not a receiving deadline. */
fun collaborationSpaceRecipientReady(
    spaceId: UUID,
    accountId: UUID,
    serverName: String,
    matrixUserId: String,
    at: Instant,
    devices: Collection<CollaborationSpaceRecipientDevice>,
): Result<Boolean> {
    if (
        devices.size > 5 ||
            devices.map { it.deviceId }.distinct().size != devices.size ||
            devices.map { it.matrixUserId to it.matrixDeviceId }.distinct().size != devices.size ||
            devices.any {
                !validCollaborationSpaceRecipient(it) ||
                    it.spaceId != spaceId ||
                    it.accountId != accountId ||
                    it.serverName != serverName ||
                    it.matrixUserId != matrixUserId ||
                    it.receiveUntil <= at
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_recipients_changed"))
    return Result.Success(devices.any { it.publicationConfirmed })
}
