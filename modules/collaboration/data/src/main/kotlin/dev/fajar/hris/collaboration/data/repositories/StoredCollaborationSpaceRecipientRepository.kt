package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceRecipientDataSource
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRecipientDevice
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceRecipient
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRecipientRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class StoredCollaborationSpaceRecipientRepository(
    private val source: CollaborationSpaceRecipientDataSource
) : CollaborationSpaceRecipientRepository {
    override fun devices(
        spaceId: UUID,
        accountIds: Set<UUID>,
        at: Instant,
    ): Result<List<CollaborationSpaceRecipientDevice>> {
        val captured = accountIds.asSequence().take(101).toSet()
        if (
            spaceId == UUID(0, 0) ||
                captured.isEmpty() ||
                captured.size > 100 ||
                UUID(0, 0) in captured ||
                at.epochSecond !in -62135596800L..253402300799L
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_key_query"))
        return safeDatabaseCall {
                val rows = source.devices(spaceId, captured, at.atOffset(UTC), 501)
                require(rows.size <= 501)
                rows
                    .map { row ->
                        require(row.spaceId == spaceId && row.accountId in captured)
                        CollaborationSpaceRecipientDevice(
                                spaceId,
                                requireNotNull(row.accountId),
                                requireNotNull(row.deviceId),
                                requireNotNull(row.matrixServerName),
                                requireNotNull(row.matrixUserId),
                                requireNotNull(row.matrixDeviceId),
                                requireNotNull(row.fingerprint),
                                requireNotNull(row.receiveUntil).toInstant(),
                                requireNotNull(row.publicationConfirmed),
                            )
                            .also {
                                require(
                                    validCollaborationSpaceRecipient(it) && it.receiveUntil > at
                                )
                            }
                    }
                    .also { rows ->
                        require(rows.map { it.deviceId }.toSet().size == rows.size)
                        require(
                            rows.map { it.matrixUserId to it.matrixDeviceId }.toSet().size ==
                                rows.size
                        )
                    }
            }
            .flatMap { rows ->
                if (
                    rows.size > 500 ||
                        rows.groupingBy { it.accountId }.eachCount().values.any { it > 5 }
                )
                    Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_recipient_device_limit")
                    )
                else Result.Success(rows)
            }
    }
}
