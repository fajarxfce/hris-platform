package dev.fajar.hris.collaboration.data.matrix

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private val states =
    setOf(
        "m.room.create",
        "m.room.member",
        "m.room.encryption",
        "m.room.join_rules",
        "m.room.history_visibility",
        "m.room.guest_access",
        "m.room.power_levels",
        "m.room.server_acl",
        "m.room.canonical_alias",
        "org.hris.conversation",
    )

/** Maps a full or stripped client event only within the use case's captured DIRECT ownership. */
fun mapCollaborationDirectRoomEvent(
    value: JsonNode,
    pair: Set<String>,
    ownUser: String,
    roomId: String,
    creator: String,
    stripped: Boolean = false,
): Map<String, Any>? {
    objectNode(value)
    val type = text(value, "type", 128)
    val sender = text(value, "sender", 255)
    if (sender !in pair || (value.has("room_id") && value.path("room_id").asString() != roomId))
        throw MatrixResponseException()
    if (type == "m.room.message") throw MatrixResponseException()
    if (type !in states && type != "m.room.encrypted" && type != "m.room.redaction") return null
    if (stripped && type !in states) throw MatrixResponseException()
    val redactionEvent = if (type == "m.room.redaction") redaction(value, pair, roomId) else null
    val content = value.path("content")
    objectNode(content)
    val unsigned = if (stripped) eventJson.createObjectNode() else optionalObject(value, "unsigned")
    val because =
        if (unsigned.has("redacted_because"))
            redaction(unsigned.path("redacted_because"), pair, roomId).also {
                val target = (it["content"] as Map<*, *>)["redacts"] ?: it["redacts"]
                if (target != text(value, "event_id", 255)) throw MatrixResponseException()
            }
        else null
    if (type in states) {
        if (!value.path("state_key").isString) throw MatrixResponseException()
        if (type == "m.room.member") {
            if (value.path("state_key").asString() !in pair) throw MatrixResponseException()
        } else if (value.path("state_key").asString() != "") throw MatrixResponseException()
    }
    if (
        type == "m.room.create" &&
            (sender != creator ||
                content.path("room_version").asString() != "12" ||
                content.path("m.federate").asBoolean(true) ||
                (content.has("additional_creators") &&
                    (!content.path("additional_creators").isArray ||
                        !content.path("additional_creators").isEmpty)))
    )
        throw MatrixResponseException()
    if (
        type == "m.room.encryption" &&
            content.path("algorithm").asString() != "m.megolm.v1.aes-sha2"
    )
        throw MatrixResponseException()
    if (
        type == "m.room.encrypted" &&
            (if (because != null) !content.isEmpty
            else
                content.path("algorithm").asString() != "m.megolm.v1.aes-sha2" ||
                    !content.path("ciphertext").isString ||
                    content.path("ciphertext").asString().length !in 1..1_048_576)
    )
        throw MatrixResponseException()
    val result =
        linkedMapOf<String, Any>("type" to type, "sender" to sender, "content" to map(content))
    redactionEvent?.get("redacts")?.let { result["redacts"] = it }
    if (value.has("state_key")) result["state_key"] = value.path("state_key").asString()
    if (!stripped) {
        result["event_id"] =
            text(value, "event_id", 255).also {
                if (!it.startsWith('$')) throw MatrixResponseException()
            }
        val time = value.path("origin_server_ts")
        if (!time.isIntegralNumber || !time.canConvertToLong() || time.asLong() < 0)
            throw MatrixResponseException()
        result["origin_server_ts"] = time.asLong()
        val txn = unsigned.path("transaction_id")
        val retained = linkedMapOf<String, Any>()
        if (sender == ownUser && txn.isString && txn.asString().length in 1..128)
            retained["transaction_id"] = txn.asString()
        if (because != null) retained["redacted_because"] = because
        if (retained.isNotEmpty()) result["unsigned"] = retained
    }
    return result
}

private val eventJson = JsonMapper.builder().build()

/** A single protocol event; never recurse through nested unsigned metadata. */
private fun redaction(value: JsonNode, pair: Set<String>, roomId: String): Map<String, Any> {
    objectNode(value)
    if (text(value, "type", 128) != "m.room.redaction") throw MatrixResponseException()
    val sender = text(value, "sender", 255)
    if (sender !in pair || (value.has("room_id") && value.path("room_id").asString() != roomId))
        throw MatrixResponseException()
    val id = text(value, "event_id", 255)
    if (!id.startsWith('$')) throw MatrixResponseException()
    val content = value.path("content")
    objectNode(content)
    val modern = if (content.has("redacts")) text(content, "redacts", 255) else null
    val legacy = if (value.has("redacts")) text(value, "redacts", 255) else null
    val target = modern ?: legacy ?: throw MatrixResponseException()
    if (
        !target.startsWith('$') ||
            target == id ||
            (modern != null && legacy != null && modern != legacy)
    )
        throw MatrixResponseException()
    val time = value.path("origin_server_ts")
    if (!time.isIntegralNumber || !time.canConvertToLong() || time.asLong() < 0)
        throw MatrixResponseException()
    return linkedMapOf<String, Any>(
            "type" to "m.room.redaction",
            "sender" to sender,
            "event_id" to id,
            "origin_server_ts" to time.asLong(),
            "content" to map(content),
        )
        .also { if (legacy != null) it["redacts"] = legacy }
}

private fun map(node: JsonNode): Map<String, Any> =
    eventJson.convertValue(node, object : TypeReference<Map<String, Any>>() {})

private fun objectNode(node: JsonNode) {
    if (!node.isObject) throw MatrixResponseException()
}

private fun optionalObject(node: JsonNode, name: String): JsonNode =
    node.path(name).let {
        if (it.isMissingNode) eventJson.createObjectNode() else it.also(::objectNode)
    }

private fun text(node: JsonNode, name: String, limit: Int): String {
    val value = node.path(name)
    if (
        !value.isString ||
            value.asString().length !in 1..limit ||
            value.asString().any { it.code !in 33..126 }
    )
        throw MatrixResponseException()
    return value.asString()
}
