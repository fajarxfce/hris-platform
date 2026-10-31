package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixRoomRepository : CollaborationMatrixRoomRepository {
    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))

    override fun resolve(
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<String?> = unavailable()

    override fun create(
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomCreation> = unavailable()

    override fun observe(
        roomId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomObservation> = unavailable()

    override fun putInitialSetting(
        roomId: String,
        setting: CollaborationMatrixRoomSetting,
        token: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()

    override fun invitePeer(
        roomId: String,
        request: CollaborationMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()
}
