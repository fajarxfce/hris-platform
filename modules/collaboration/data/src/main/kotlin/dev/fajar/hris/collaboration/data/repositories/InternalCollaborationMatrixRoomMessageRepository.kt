package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomMessageRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

class InternalCollaborationMatrixRoomMessageRepository(
    private val source: MatrixRoomMessageDataSource
) : CollaborationMatrixRoomMessageRepository {
    override fun send(
        roomId: String,
        message: CollaborationRoomMessage,
        bearer: String,
        deadline: Instant,
    ): Result<String> = safeMatrixProviderCall {
        val content =
            buildMap<String, Any> {
                put("algorithm", "m.megolm.v1.aes-sha2")
                put("ciphertext", message.ciphertext)
                put("session_id", message.sessionId)
                message.senderKey?.let { put("sender_key", it) }
                message.deviceId?.let { put("device_id", it) }
            }
        val response =
            source.sendRoomMessage(roomId, message.transactionId, content, bearer, deadline)
        val eventId = response.path("event_id")
        if (
            !response.isObject ||
                response.propertyNames() != setOf("event_id") ||
                !eventId.isString ||
                eventId.asString().length !in 2..255 ||
                !eventId.asString().startsWith('$') ||
                eventId.asString().any { it.code !in 33..126 }
        )
            throw MatrixResponseException()
        eventId.asString()
    }
}
