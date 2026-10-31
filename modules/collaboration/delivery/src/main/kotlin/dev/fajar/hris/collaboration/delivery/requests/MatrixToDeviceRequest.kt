package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationOlmEnvelope
import dev.fajar.hris.collaboration.domain.entities.CollaborationToDevice
import tools.jackson.databind.JsonNode

fun matrixToDeviceRequest(transactionId: String, body: JsonNode): CollaborationToDevice {
    require(body.isObject && body.propertyNames() == setOf("messages"))
    val messages = body.path("messages")
    require(messages.isObject && messages.size() in 1..100)
    return CollaborationToDevice(
        transactionId,
        messages.properties().associate { (user, devices) ->
            require(user.length in 1..255 && devices.isObject && devices.size() in 1..5)
            user to
                devices.properties().associate { (device, value) ->
                    require(
                        device.length in 1..128 &&
                            value.isObject &&
                            value.propertyNames().all {
                                it in
                                    setOf(
                                        "algorithm",
                                        "sender_key",
                                        "ciphertext",
                                        "org.matrix.msgid",
                                    )
                            }
                    )
                    require(value.path("algorithm").asString() == "m.olm.v1.curve25519-aes-sha2")
                    val ciphertext = value.path("ciphertext")
                    require(ciphertext.isObject && ciphertext.size() == 1)
                    val entry = ciphertext.properties().single()
                    require(entry.key.length in 43..44)
                    val content = entry.value
                    require(content.isObject && content.propertyNames() == setOf("type", "body"))
                    val type = content.path("type")
                    require(type.isIntegralNumber && type.canConvertToInt() && type.asInt() in 0..1)
                    val messageId = value.get("org.matrix.msgid")?.let { text(it, 128) }
                    device to
                        CollaborationOlmEnvelope(
                            text(value.path("sender_key"), 44),
                            entry.key,
                            text(content.path("body"), 65536),
                            type.asInt(),
                            messageId,
                        )
                }
        },
    )
}

private fun text(node: JsonNode, maximum: Int): String {
    require(node.isString && node.asString().length in 1..maximum)
    return node.asString()
}
