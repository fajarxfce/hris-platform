package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.collaboration.domain.policies.COLLABORATION_ROOM_CIPHERTEXT_LIMIT
import tools.jackson.databind.JsonNode

fun matrixRoomMessageRequest(transactionId: String, body: JsonNode): CollaborationRoomMessage {
    require(
        body.isObject &&
            body.propertyNames().all {
                it in setOf("algorithm", "ciphertext", "session_id", "sender_key", "device_id")
            }
    )
    require(body.path("algorithm").asString() == "m.megolm.v1.aes-sha2")
    return CollaborationRoomMessage(
        transactionId,
        text(body.path("ciphertext"), COLLABORATION_ROOM_CIPHERTEXT_LIMIT),
        text(body.path("session_id"), 44),
        body.get("sender_key")?.let { text(it, 44) },
        body.get("device_id")?.let { text(it, 128) },
    )
}

private fun text(value: JsonNode, maximum: Int): String {
    require(value.isString && value.asString().length in 1..maximum)
    return value.asString()
}
