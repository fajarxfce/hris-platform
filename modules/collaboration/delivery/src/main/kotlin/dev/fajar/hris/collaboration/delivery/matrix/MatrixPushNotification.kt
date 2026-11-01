package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationPushEvent
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

const val MATRIX_PUSH_MAXIMUM_BYTES = 8192
const val MATRIX_PUSH_MAXIMUM_DEVICES = 32

private val pushParser =
    JsonMapper.builder(
            JsonFactory.builder()
                .streamReadConstraints(
                    StreamReadConstraints.builder()
                        .maxNestingDepth(6)
                        .maxStringLength(256)
                        .maxNumberLength(20)
                        .build()
                )
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build()
        )
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build()

/**
 * Fixed event_id_only profile. Legacy Synapse badge sentinels are accepted only when empty;
 * content, names, arbitrary device data and tweaks are rejected. Counts never become authority. The
 * HTTP owner must also bound stream size/time before acquiring this byte array.
 */
fun matrixPushNotification(bytes: ByteArray): CollaborationPushHint? {
    if (bytes.size !in 2..MATRIX_PUSH_MAXIMUM_BYTES) return null
    val root =
        try {
            pushParser.readTree(bytes)
        } catch (_: JacksonException) {
            return null
        } ?: return null
    if (!root.isObject || root.propertyNames().toSet() != setOf("notification")) return null
    val node = root["notification"]
    if (
        !node.isObject ||
            node.propertyNames().any {
                it !in
                    setOf(
                        "event_id",
                        "room_id",
                        "prio",
                        "counts",
                        "devices",
                        "id",
                        "type",
                        "sender",
                    )
            }
    )
        return null
    val event =
        if (node.has("event_id") || node.has("room_id")) {
            if (!node.path("event_id").isString || !node.path("room_id").isString) return null
            CollaborationPushEvent(node["room_id"].asString(), node["event_id"].asString())
                .takeIf(::validCollaborationPushEvent) ?: return null
        } else null
    if (
        node.has("prio") &&
            (!node["prio"].isString || node["prio"].asString() !in setOf("high", "low"))
    )
        return null
    for (name in listOf("id", "sender")) {
        if (
            node.has(name) && (event != null || !node[name].isString || node[name].asString() != "")
        )
            return null
    }
    if (node.has("type") && (event != null || !node["type"].isNull)) return null
    if (node.has("counts")) {
        val counts = node["counts"]
        if (
            !counts.isObject ||
                counts.propertyNames().any { it !in setOf("unread", "missed_calls") } ||
                counts.properties().any {
                    !nonnegativeLong(it.value) || it.value.asLong() > Int.MAX_VALUE
                }
        )
            return null
        if (event == null && counts.isEmpty) return null
    } else if (event == null) return null
    val devices = node.path("devices")
    if (!devices.isArray || devices.size() !in 1..MATRIX_PUSH_MAXIMUM_DEVICES) return null
    val keys = ArrayList<CollaborationPushKey>(devices.size())
    for (device in devices) {
        if (
            !device.isObject ||
                device.propertyNames().any {
                    it !in setOf("app_id", "pushkey", "pushkey_ts", "data", "tweaks")
                } ||
                !device.path("app_id").isString ||
                device["app_id"].asString() != "dev.fajar.hris.chat" ||
                !device.path("pushkey").isString
        )
            return null
        val key = device["pushkey"].asString()
        if (!key.matches(Regex("hriscp1_[a-f0-9]{64}")) || keys.any { it.value == key }) return null
        if (device.has("pushkey_ts") && !nonnegativeLong(device["pushkey_ts"])) return null
        if (device.has("data")) {
            val data = device["data"]
            if (
                !data.isObject ||
                    data.propertyNames().any { it != "format" } ||
                    (data.has("format") &&
                        (!data["format"].isString || data["format"].asString() != "event_id_only"))
            )
                return null
        }
        if (device.has("tweaks") && (!device["tweaks"].isObject || !device["tweaks"].isEmpty))
            return null
        keys += CollaborationPushKey(key)
    }
    return CollaborationPushHint(event, keys)
}

private fun nonnegativeLong(node: JsonNode): Boolean =
    node.isIntegralNumber && node.canConvertToLong() && node.asLong() >= 0
