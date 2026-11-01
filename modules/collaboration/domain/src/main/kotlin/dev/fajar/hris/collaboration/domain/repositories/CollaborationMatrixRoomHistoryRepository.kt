package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixRoomHistoryRepository {
    fun messages(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory>

    fun context(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter = CollaborationRoomHistoryFilter(),
    ): Result<CollaborationRoomHistory>

    fun event(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory>
}
