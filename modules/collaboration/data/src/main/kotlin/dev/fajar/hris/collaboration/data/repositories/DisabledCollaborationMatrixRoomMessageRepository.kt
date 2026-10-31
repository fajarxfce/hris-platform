package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomMessageRepository
import dev.fajar.hris.core.domain.*
import java.time.Instant

class DisabledCollaborationMatrixRoomMessageRepository : CollaborationMatrixRoomMessageRepository {
    override fun send(
        roomId: String,
        message: CollaborationRoomMessage,
        bearer: String,
        deadline: Instant,
    ): Result<String> = Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
}
