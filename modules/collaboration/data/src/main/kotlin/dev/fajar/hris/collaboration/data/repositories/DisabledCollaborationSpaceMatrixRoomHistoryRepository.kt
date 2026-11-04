package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomHistoryRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSpaceMatrixRoomHistoryRepository :
    CollaborationSpaceMatrixRoomHistoryRepository {
    override fun messages(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = unavailable()

    override fun context(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter,
    ): Result<CollaborationRoomHistory> = unavailable()

    override fun event(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = unavailable()

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
