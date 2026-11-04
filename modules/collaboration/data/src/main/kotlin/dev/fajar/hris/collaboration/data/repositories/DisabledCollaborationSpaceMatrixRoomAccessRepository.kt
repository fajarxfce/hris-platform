package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomAccessRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSpaceMatrixRoomAccessRepository :
    CollaborationSpaceMatrixRoomAccessRepository {
    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))

    override fun join(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String> = unavailable()

    override fun receipt(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()

    override fun state(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = unavailable()

    override fun members(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = unavailable()
}
