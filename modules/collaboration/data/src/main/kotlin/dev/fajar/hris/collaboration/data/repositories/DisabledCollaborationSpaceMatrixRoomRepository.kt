package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationSpaceMatrixRoomRepository : CollaborationSpaceMatrixRoomRepository {
    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))

    override fun resolve(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<String?> = unavailable()

    override fun create(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomCreation> = unavailable()

    override fun join(roomId: String, token: String, deadline: Instant): Result<Unit> =
        unavailable()

    override fun observe(
        roomId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationSpaceMatrixRoomObservation> = unavailable()

    override fun putInitialSetting(
        roomId: String,
        setting: CollaborationMatrixRoomSetting,
        token: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()

    override fun invitePeer(
        roomId: String,
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<Unit> = unavailable()
}
