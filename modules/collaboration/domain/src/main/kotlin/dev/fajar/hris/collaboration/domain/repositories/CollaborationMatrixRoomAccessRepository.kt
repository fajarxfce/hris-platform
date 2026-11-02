package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixRoomAccessRepository {
    fun join(room: CollaborationOwnedRoom, bearer: String, deadline: Instant): Result<String>

    /** Idempotent provider metadata; the caller authorizes the exact original event first. */
    fun receipt(
        room: CollaborationOwnedRoom,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<Unit>

    fun state(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents>

    fun members(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents>
}
