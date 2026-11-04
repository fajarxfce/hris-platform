package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRoomRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import tools.jackson.databind.JsonNode

class InternalCollaborationSpaceMatrixRoomRepository(
    private val client: MatrixRoomDataSource,
    private val serverName: String,
) : CollaborationSpaceMatrixRoomRepository {
    override fun resolve(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<String?> = safeMatrixProviderCall {
        require(validCollaborationSpaceRoomRequest(request, serverName))
        client
            .resolveAlias(
                "#${collaborationSpaceMatrixRoomAliasLocalpart(request)}:$serverName",
                token,
                deadline,
            )
            ?.also { require(validCollaborationSpaceRoomId(it)) }
    }

    override fun create(
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixRoomCreation> = safeMatrixProviderCall {
        require(validCollaborationSpaceRoomRequest(request, serverName))
        val body =
            mapOf(
                "room_version" to "12",
                "visibility" to "private",
                "preset" to "private_chat",
                "room_alias_name" to collaborationSpaceMatrixRoomAliasLocalpart(request),
                "is_direct" to true,
                "creation_content" to
                    mapOf(
                        "m.federate" to false,
                        "org.hris.space_conversation" to
                            mapOf(
                                "space_id" to request.spaceId.toString(),
                                "conversation_id" to request.conversationId.toString(),
                                "kind" to "DIRECT",
                            ),
                    ),
                "power_level_content_override" to
                    content(CollaborationMatrixRoomSetting.POWER_LEVELS),
                "initial_state" to
                    CollaborationMatrixRoomSetting.entries
                        .filter { it != CollaborationMatrixRoomSetting.POWER_LEVELS }
                        .map {
                            mapOf(
                                "type" to MatrixRoomStateType.valueOf(it.name).eventType,
                                "state_key" to "",
                                "content" to content(it),
                            )
                        },
                "invite" to listOf(peer(request)),
            )
        when (val result = client.createRoom(body, token, deadline)) {
            is MatrixRoomCreateData.Created ->
                CollaborationMatrixRoomCreation.Created(
                    result.roomId.also { require(validCollaborationSpaceRoomId(it)) }
                )
            MatrixRoomCreateData.AliasInUse -> CollaborationMatrixRoomCreation.AliasInUse
        }
    }

    override fun observe(
        roomId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationSpaceMatrixRoomObservation> = safeMatrixProviderCall {
        require(validCollaborationSpaceRoomId(roomId))
        val events = client.roomState(roomId, token, deadline)
        if (events.size > 256 || events.map { it.type to it.stateKey }.toSet().size != events.size)
            throw MatrixResponseException()
        fun event(type: String) = events.singleOrNull { it.type == type && it.stateKey == "" }
        val creation = event("m.room.create")
        val create = creation?.content
        val marker =
            create?.get("org.hris.space_conversation")?.let {
                if (!it.isObject) throw MatrixResponseException()
                CollaborationSpaceMatrixRoomMarker(
                    text(it, "space_id"),
                    text(it, "conversation_id"),
                    text(it, "kind"),
                )
            }
        fun field(type: String, key: String) = event(type)?.let { text(it.content, key) }
        val creators =
            create?.get("additional_creators")?.let {
                if (!it.isArray || it.size() > 128) throw MatrixResponseException()
                it.iterator().asSequence().map { item -> string(item) }.toList()
            } ?: emptyList()
        val federated =
            create?.get("m.federate")?.let {
                if (!it.isBoolean) throw MatrixResponseException()
                it.asBoolean()
            }
        val power =
            event("m.room.power_levels")?.content?.let {
                CollaborationMatrixRoomPower(
                    numbers(it, "users"),
                    numbers(it, "events"),
                    number(it, "users_default"),
                    number(it, "events_default"),
                    number(it, "state_default"),
                    number(it, "invite"),
                    number(it, "kick"),
                    number(it, "ban"),
                    number(it, "redact"),
                )
            }
        CollaborationSpaceMatrixRoomObservation(
            creation?.sender,
            create?.let { text(it, "room_version") },
            federated,
            marker,
            creators,
            event("m.room.tombstone") != null,
            field("m.room.encryption", "algorithm"),
            field("m.room.join_rules", "join_rule"),
            field("m.room.history_visibility", "history_visibility"),
            field("m.room.guest_access", "guest_access"),
            power,
            events
                .filter { it.type == "m.room.member" }
                .associate { it.stateKey to text(it.content, "membership") },
        )
    }

    override fun putInitialSetting(
        roomId: String,
        setting: CollaborationMatrixRoomSetting,
        token: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        require(validCollaborationSpaceRoomId(roomId))
        client.putRoomState(
            roomId,
            MatrixRoomStateType.valueOf(setting.name),
            content(setting),
            token,
            deadline,
        )
    }

    override fun invitePeer(
        roomId: String,
        request: CollaborationSpaceMatrixDirectRoom,
        token: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        require(
            validCollaborationSpaceRoomId(roomId) &&
                validCollaborationSpaceRoomRequest(request, serverName)
        )
        client.invite(roomId, peer(request), token, deadline)
    }

    private fun peer(request: CollaborationSpaceMatrixDirectRoom) = request.peerUserId

    private fun content(setting: CollaborationMatrixRoomSetting): Map<String, Any> =
        when (setting) {
            CollaborationMatrixRoomSetting.POWER_LEVELS ->
                collaborationDirectRoomPower().let {
                    mapOf(
                        "users" to it.users,
                        "events" to it.events,
                        "users_default" to it.usersDefault,
                        "events_default" to it.eventsDefault,
                        "state_default" to it.stateDefault,
                        "invite" to it.invite,
                        "kick" to it.kick,
                        "ban" to it.ban,
                        "redact" to it.redact,
                    )
                }
            CollaborationMatrixRoomSetting.ENCRYPTION ->
                mapOf("algorithm" to "m.megolm.v1.aes-sha2")
            CollaborationMatrixRoomSetting.JOIN_RULES -> mapOf("join_rule" to "invite")
            CollaborationMatrixRoomSetting.HISTORY_VISIBILITY ->
                mapOf("history_visibility" to "invited")
            CollaborationMatrixRoomSetting.GUEST_ACCESS -> mapOf("guest_access" to "forbidden")
        }

    private fun text(node: JsonNode, key: String): String = string(node.path(key))

    private fun string(node: JsonNode): String {
        if (!node.isString) throw MatrixResponseException()
        return node.asString().also {
            if (it.length !in 1..255 || it.any { char -> char.code !in 33..126 })
                throw MatrixResponseException()
        }
    }

    private fun number(node: JsonNode, key: String): Long {
        val value = node.path(key)
        if (!value.isIntegralNumber || !value.canConvertToLong()) throw MatrixResponseException()
        return value.asLong()
    }

    private fun numbers(node: JsonNode, key: String): Map<String, Long> {
        val value = node.path(key)
        if (!value.isObject || value.size() > 128) throw MatrixResponseException()
        return value.propertyNames().associateWith { name ->
            if (name.length !in 1..255 || name.any { it.code !in 33..126 })
                throw MatrixResponseException()
            number(value, name)
        }
    }
}
