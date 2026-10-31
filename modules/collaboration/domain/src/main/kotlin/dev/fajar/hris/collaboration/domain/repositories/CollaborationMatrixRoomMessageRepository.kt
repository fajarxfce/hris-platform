package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.core.domain.Result
import java.time.Instant

interface CollaborationMatrixRoomMessageRepository {
    fun send(
        roomId: String,
        message: CollaborationRoomMessage,
        bearer: String,
        deadline: Instant,
    ): Result<String>
}
