package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixRoomRepository {
    fun resolve(
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<String?>

    fun create(
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomCreation>

    fun observe(
        roomId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomObservation>

    fun putInitialSetting(
        roomId: String,
        setting: CollaborationMatrixRoomSetting,
        token: String,
        deadline: Instant,
    ): Result<Unit>

    fun invitePeer(
        roomId: String,
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<Unit>
}
