package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixSyncRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Maps only the fixed DIRECT protocol surface. Provider filters are an optimization, never ACLs.
 */
class InternalCollaborationMatrixSyncRepository(private val source: MatrixSyncDataSource) :
    CollaborationMatrixSyncRepository {
    private val json = JsonMapper.builder().build()
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

    override fun sync(
        owner: CollaborationSyncOwner,
        scope: CollaborationRoomScope,
        position: CollaborationSyncPosition?,
        timeoutMillis: Int,
        fullState: Boolean,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationMatrixSyncBatch> = safeMatrixProviderCall {
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        val ownUser = user(owner.accountId)
        val rooms = scope.rooms.associateBy { it.roomId }
        val allowedUsers =
            scope.rooms
                .flatMap { listOf(user(it.firstAccountId), user(it.secondAccountId)) }
                .toSet() + ownUser
        source.open(ownUser, collaborationMatrixDeviceId(owner.deviceId), bearer, deadline).use {
            connection ->
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
                                    "ephemeral" to mapOf("not_types" to listOf("*")),
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
                    val pair = setOf(user(room.firstAccountId), user(room.secondAccountId))
                    val creator = user(room.creatorAccountId)
                    if (bucket == "invite") {
                        val invited =
                            events(value, "invite_state").mapNotNull {
                                event(it, pair, ownUser, id, creator, stripped = true)
                            }
                        output[id] = mapOf("invite_state" to mapOf("events" to invited))
                    } else {
                        val state =
                            events(value, "state")
                                .mapNotNull { event(it, pair, ownUser, id, creator) }
                                .toMutableList()
                        val timelineNode = optionalObject(value, "timeline")
                        val timeline =
                            eventArray(timelineNode).mapNotNull {
                                event(it, pair, ownUser, id, creator)
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
                                event(creates.single(), pair, ownUser, id, creator)
                                    ?: throw MatrixResponseException(),
                            )
                        }
                        val timelineOut = linkedMapOf<String, Any>("events" to timeline)
                        val previous = timelineNode.path("prev_batch")
                        if (!previous.isMissingNode)
                            timelineOut["prev_batch"] = text(timelineNode, "prev_batch", 1024)
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
                    if (text(envelope, "type", 128) != "m.room.encrypted") return@mapNotNull null
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
            val direct =
                scope.rooms
                    .groupBy {
                        user(
                            if (it.firstAccountId == owner.accountId) it.secondAccountId
                            else it.firstAccountId
                        )
                    }
                    .mapValues { it.value.map { room -> room.roomId } }
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

    private fun event(
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
        val content = value.path("content")
        objectNode(content)
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
                (content.path("algorithm").asString() != "m.megolm.v1.aes-sha2" ||
                    !content.path("ciphertext").isString ||
                    content.path("ciphertext").asString().length !in 1..1_048_576)
        )
            throw MatrixResponseException()
        val result =
            linkedMapOf<String, Any>("type" to type, "sender" to sender, "content" to map(content))
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
            val unsigned = optionalObject(value, "unsigned")
            val txn = unsigned.path("transaction_id")
            if (sender == ownUser && txn.isString && txn.asString().length in 1..128)
                result["unsigned"] = mapOf("transaction_id" to txn.asString())
        }
        return result
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
