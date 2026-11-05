package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomMessage
import dev.fajar.hris.collaboration.domain.policies.COLLABORATION_ROOM_CIPHERTEXT_LIMIT
import tools.jackson.databind.JsonNode

fun matrixRoomMessageRequest(transactionId: String, body: JsonNode): CollaborationRoomMessage {
    require(
        body.isObject &&
            body.propertyNames().all {
                it in
                    setOf(
                        "algorithm",
                        "ciphertext",
                        "session_id",
                        "sender_key",
                        "device_id",
                        "m.relates_to",
                    )
            }
    )
    require(body.path("algorithm").asString() == "m.megolm.v1.aes-sha2")
    val relation = body.get("m.relates_to")?.let(::messageRelation)
    return CollaborationRoomMessage(
        transactionId,
        text(body.path("ciphertext"), COLLABORATION_ROOM_CIPHERTEXT_LIMIT),
        text(body.path("session_id"), 44),
        body.get("sender_key")?.let { text(it, 44) },
        body.get("device_id")?.let { text(it, 128) },
        replyToEventId = relation?.first,
        replacesEventId = relation?.second,
    )
}

/** Only the two exact opaque relation shapes supported by the encrypted client. */
private fun messageRelation(value: JsonNode): Pair<String?, String?> {
    require(value.isObject)
    return when (value.propertyNames()) {
        setOf("m.in_reply_to") -> {
            val target = value.path("m.in_reply_to")
            require(target.isObject && target.propertyNames() == setOf("event_id"))
            text(target.path("event_id"), 255) to null
        }
        setOf("rel_type", "event_id") -> {
            require(text(value.path("rel_type"), 32) == "m.replace")
            null to text(value.path("event_id"), 255)
        }
        else -> throw IllegalArgumentException("Unsupported encrypted message relation.")
    }
}

private fun text(value: JsonNode, maximum: Int): String {
    require(value.isString && value.asString().length in 1..maximum)
    return value.asString()
}
