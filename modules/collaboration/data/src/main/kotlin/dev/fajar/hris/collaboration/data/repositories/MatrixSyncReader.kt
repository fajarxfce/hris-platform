package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import java.time.Instant
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Shared protocol acquisition/projection; all caller admission and framing remain explicit. */
internal class MatrixSyncReader(private val source: MatrixSyncDataSource) {
    private val json = JsonMapper.builder().build()

    fun sync(
        frame: MatrixSyncFrame,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): CollaborationMatrixSyncBatch {
        val ownUser = frame.ownUser
        val rooms = frame.rooms
        val allowedUsers = rooms.values.flatMap { it.users }.toSet() + ownUser
        return source.open(ownUser, frame.ownDevice, bearer, deadline).use { connection ->
            // One stable filter per provider user/revision avoids retaining a growing
            // room-list copy for every new DM. Every response bucket is scoped below.
            val filter =
                position?.providerFilterId
                    ?: connection.registerFilter(
                        mapOf(
                            "presence" to mapOf("not_types" to listOf("*")),
                            "account_data" to mapOf("not_types" to listOf("*")),
                            "room" to
                                mapOf(
                                    "include_leave" to true,
                                    "state" to mapOf("lazy_load_members" to false),
                                    "timeline" to mapOf("limit" to 20),
                                    "ephemeral" to mapOf("types" to listOf("m.receipt")),
                                    "account_data" to mapOf("not_types" to listOf("*")),
                                ),
                        )
                    )
            val response =
                connection.sync(position?.providerSince, filter, timeoutMillis, fullState)
            objectNode(response)
            val next = text(response, "next_batch", 1024)
            val roomBuckets = optionalObject(response, "rooms")
            val outputRooms = linkedMapOf<String, Any>()
            for (bucket in listOf("join", "invite", "leave")) {
                val entries = optionalObject(roomBuckets, bucket)
                val output = linkedMapOf<String, Any>()
                for ((id, value) in entries.properties()) {
                    val room = rooms[id] ?: continue
                    objectNode(value)
                    val pair = room.users
                    val creator = room.creator
                    if (bucket == "invite") {
                        val invited =
                            events(value, "invite_state").mapNotNull {
                                mapCollaborationDirectRoomEvent(
                                    it,
                                    pair,
                                    ownUser,
                                    id,
                                    creator,
                                    stripped = true,
                                )
                            }
                        output[id] = mapOf("invite_state" to mapOf("events" to invited))
                    } else {
                        val state =
                            events(value, "state")
                                .mapNotNull {
                                    mapCollaborationDirectRoomEvent(it, pair, ownUser, id, creator)
                                }
                                .toMutableList()
                        val timelineNode = optionalObject(value, "timeline")
                        val timeline =
                            eventArray(timelineNode).mapNotNull {
                                mapCollaborationDirectRoomEvent(it, pair, ownUser, id, creator)
                            }
                        if (
                            (position == null || fullState) &&
                                bucket == "join" &&
                                (state + timeline).none { it["type"] == "m.room.create" }
                        ) {
                            val current = connection.roomState(id)
                            if (!current.isArray || current.size() > 256)
                                throw MatrixResponseException()
                            val creates =
                                current.filter {
                                    it.path("type").asString() == "m.room.create" &&
                                        it.path("state_key").asString() == ""
                                }
                            if (creates.size != 1) throw MatrixResponseException()
                            state.add(
                                0,
                                mapCollaborationDirectRoomEvent(
                                    creates.single(),
                                    pair,
                                    ownUser,
                                    id,
                                    creator,
                                ) ?: throw MatrixResponseException(),
                            )
                        }
                        val timelineOut = linkedMapOf<String, Any>("events" to timeline)
                        val previous = timelineNode.path("prev_batch")
                        if (!previous.isMissingNode)
                            timelineOut["prev_batch"] =
                                frame.sealHistory(id, text(timelineNode, "prev_batch", 1024))
                        val limited = timelineNode.path("limited")
                        if (!limited.isMissingNode) {
                            if (!limited.isBoolean) throw MatrixResponseException()
                            timelineOut["limited"] = limited.asBoolean()
                        }
                        output[id] =
                            mapOf(
                                "state" to mapOf("events" to state),
                                "timeline" to timelineOut,
                                "account_data" to mapOf("events" to emptyList<Any>()),
                                "ephemeral" to
                                    mapOf(
                                        "events" to
                                            mapCollaborationReadReceipts(
                                                events(value, "ephemeral"),
                                                pair,
                                                ownUser,
                                            )
                                    ),
                            )
                    }
                }
                outputRooms[bucket] = output
            }
            val toDevice =
                events(response, "to_device").mapNotNull { envelope ->
                    objectNode(envelope)
                    val sender = text(envelope, "sender", 255)
                    if (sender !in allowedUsers) return@mapNotNull null
                    val type = text(envelope, "type", 128)
                    if (type == COLLABORATION_READINESS_CONTROL_TYPE) {
                        val owner = frame.readinessOwner ?: return@mapNotNull null
                        // The public gateway cannot send this clear event type. Only the
                        // target's own provider identity may carry a bounded opaque hint.
                        if (sender != ownUser) return@mapNotNull null
                        val content = envelope.path("content")
                        if (!content.isObject || content.size() != 9) return@mapNotNull null
                        val fields = linkedMapOf<String, String>()
                        for ((name, value) in content.properties()) {
                            if (!value.isString || value.asString().length > 36)
                                return@mapNotNull null
                            fields[name] = value.asString()
                        }
                        val control =
                            readCollaborationReadinessControl(fields) ?: return@mapNotNull null
                        if (
                            control.companyId != owner.companyId ||
                                control.accountId != owner.accountId ||
                                control.deviceId != owner.deviceId
                        )
                            return@mapNotNull null
                        return@mapNotNull mapOf(
                            "sender" to sender,
                            "type" to type,
                            "content" to fields,
                        )
                    }
                    if (type != "m.room.encrypted") return@mapNotNull null
                    val content = envelope.path("content")
                    objectNode(content)
                    if (content.path("algorithm").asString() != "m.olm.v1.curve25519-aes-sha2")
                        throw MatrixResponseException()
                    // Ciphertext is opaque to HRIS; the SDK authenticates and decrypts the
                    // envelope.
                    mapOf(
                        "sender" to sender,
                        "type" to "m.room.encrypted",
                        "content" to map(content),
                    )
                }
            val deviceLists = optionalObject(response, "device_lists")
            fun users(name: String): List<String> {
                val values = deviceLists.path(name)
                if (values.isMissingNode) return emptyList()
                if (!values.isArray || values.size() > 50_001) throw MatrixResponseException()
                return values
                    .toList()
                    .map {
                        if (!it.isString) throw MatrixResponseException()
                        it.asString()
                    }
                    .filter { it in allowedUsers }
                    .distinct()
            }
            val counts = optionalObject(response, "device_one_time_keys_count")
            val keyCounts = linkedMapOf<String, Long>()
            for (type in listOf("signed_curve25519", "curve25519")) {
                val count = counts.path(type)
                if (!count.isMissingNode) {
                    if (
                        !count.isIntegralNumber ||
                            !count.canConvertToLong() ||
                            count.asLong() !in 0..1_000_000
                    )
                        throw MatrixResponseException()
                    keyCounts[type] = count.asLong()
                }
            }
            val fallback = response.path("device_unused_fallback_key_types")
            if (
                !fallback.isMissingNode &&
                    (!fallback.isArray || fallback.size() > 10 || fallback.any { !it.isString })
            )
                throw MatrixResponseException()
            val direct = frame.direct
            CollaborationMatrixSyncBatch(
                next,
                filter,
                mapOf(
                    "rooms" to outputRooms,
                    "to_device" to mapOf("events" to toDevice),
                    "device_lists" to mapOf("changed" to users("changed"), "left" to users("left")),
                    "device_one_time_keys_count" to keyCounts,
                    "device_unused_fallback_key_types" to
                        (if (fallback.isMissingNode) emptyList()
                        else
                            fallback
                                .toList()
                                .map { it.asString() }
                                .filter { it in setOf("signed_curve25519", "curve25519") }
                                .distinct()),
                    "account_data" to
                        mapOf("events" to listOf(mapOf("type" to "m.direct", "content" to direct))),
                    "presence" to mapOf("events" to emptyList<Any>()),
                ),
            )
        }
    }

    private fun map(node: JsonNode): Map<String, Any> =
        json.convertValue(node, object : TypeReference<Map<String, Any>>() {})

    private fun objectNode(node: JsonNode) {
        if (!node.isObject) throw MatrixResponseException()
    }

    private fun optionalObject(node: JsonNode, name: String): JsonNode =
        node.path(name).let {
            if (it.isMissingNode) json.createObjectNode() else it.also(::objectNode)
        }

    private fun events(node: JsonNode, name: String): List<JsonNode> =
        eventArray(optionalObject(node, name))

    private fun eventArray(node: JsonNode): List<JsonNode> {
        val values = node.path("events")
        if (values.isMissingNode) return emptyList()
        if (!values.isArray || values.size() > 10_000) throw MatrixResponseException()
        return values.toList()
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
}

internal data class MatrixSyncRoomFrame(val users: Set<String>, val creator: String)

internal data class MatrixSyncFrame(
    val ownUser: String,
    val ownDevice: String,
    val rooms: Map<String, MatrixSyncRoomFrame>,
    val direct: Map<String, List<String>>,
    // Company controls stay in their original scope; group framing never inherits them.
    val readinessOwner: CollaborationSyncOwner? = null,
    val sealHistory: (String, String) -> String,
)
