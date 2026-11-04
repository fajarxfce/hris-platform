package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/**
 * Metadata transport only; caller owns current native admission and original-event receipt proof.
 */
interface CollaborationSpaceMatrixRoomAccessRepository {
    fun join(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String>

    fun receipt(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit>

    fun state(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents>

    fun members(
        owner: CollaborationSpaceHistoryOwner,
        room: CollaborationSpaceReadRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents>
}
