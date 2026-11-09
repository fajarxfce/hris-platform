package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.CollaborationRecipientDevice
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

/** Readiness is a current device fact, separate from account existence or sending authority. */
fun collaborationRecipientReady(
    companyId: UUID,
    accountId: UUID,
    at: Instant,
    devices: Collection<CollaborationRecipientDevice>,
): Result<Boolean> {
    if (
        devices.size > 5 ||
            devices.map { it.deviceId }.distinct().size != devices.size ||
            devices.any {
                it.companyId != companyId || it.accountId != accountId || it.receiveUntil <= at
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_recipients_changed"))
    return Result.Success(devices.any { it.publicationConfirmed })
}
