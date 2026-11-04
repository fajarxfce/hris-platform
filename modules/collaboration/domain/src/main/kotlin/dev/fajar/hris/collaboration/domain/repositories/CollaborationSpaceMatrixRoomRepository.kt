package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationSpaceMatrixRoomRepository {
    fun resolve(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<String?>

    fun create(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomCreation>

    fun join(roomId: String, token: String, deadline: Instant): Result<Unit>

    fun observe(
        roomId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationSpaceMatrixRoomObservation>

    fun putInitialSetting(
        roomId: String,
        setting: CollaborationMatrixRoomSetting,
        token: String,
        deadline: Instant,
    ): Result<Unit>

    fun invitePeer(
        roomId: String,
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<Unit>
}
