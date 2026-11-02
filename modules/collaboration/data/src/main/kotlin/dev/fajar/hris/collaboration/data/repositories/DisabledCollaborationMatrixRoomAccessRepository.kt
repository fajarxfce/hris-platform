package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomAccessRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixRoomAccessRepository : CollaborationMatrixRoomAccessRepository {
    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))

    override fun join(
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String> = unavailable()

    override fun receipt(
        room: CollaborationOwnedRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()

    override fun state(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = unavailable()

    override fun members(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = unavailable()
}
