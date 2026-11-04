package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Provider protocol only. The use case must admit and pin the exact owner and room. */
interface CollaborationSpaceMatrixRoomHistoryRepository {
    fun messages(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory>

    fun context(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter = CollaborationRoomHistoryFilter(),
    ): Result<CollaborationRoomHistory>

    fun event(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceBoundRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory>
}
