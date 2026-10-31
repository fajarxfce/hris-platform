package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationRecipientDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationRecipientDevice
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRecipientRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredCollaborationRecipientRepository(private val store: CollaborationRecipientDataSource) :
    CollaborationRecipientRepository {
    override fun devices(
        companyId: UUID,
        accountIds: Set<UUID>,
        at: Instant,
    ): Result<List<CollaborationRecipientDevice>> {
        if (accountIds.isEmpty() || accountIds.size > 100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_query"))
        val captured = accountIds.toSet()
        return safeDatabaseCall {
                store.devices(companyId, captured, at.atOffset(ZoneOffset.UTC), 501).map {
                    CollaborationRecipientDevice(
                        requireNotNull(it.companyId),
                        requireNotNull(it.accountId),
                        requireNotNull(it.deviceId),
                        requireNotNull(it.fingerprint),
                        requireNotNull(it.receiveUntil).toInstant(),
                    )
                }
            }
            .flatMap {
                if (it.size <= 500) Result.Success(it)
                else
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_recipient_device_limit")
                    )
            }
    }
}
