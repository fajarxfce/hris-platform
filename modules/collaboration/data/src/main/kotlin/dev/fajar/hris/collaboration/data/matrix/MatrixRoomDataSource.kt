package dev.fajar.hris.collaboration.data.matrix

import java.time.Instant
import tools.jackson.databind.JsonNode

/** Raw internal protocol operations. The use case owns room/participant authority and repair. */
interface MatrixRoomDataSource {
    fun resolveAlias(alias: String, token: String, deadline: Instant): String?

    fun createRoom(body: Map<String, Any>, token: String, deadline: Instant): MatrixRoomCreateData

    fun roomState(roomId: String, token: String, deadline: Instant): List<MatrixRoomStateEventData>

    fun putRoomState(
        roomId: String,
        type: MatrixRoomStateType,
        content: Map<String, Any>,
        token: String,
        deadline: Instant,
    )

    fun invite(roomId: String, userId: String, token: String, deadline: Instant)
}

sealed interface MatrixRoomCreateData {
    data class Created(val roomId: String) : MatrixRoomCreateData

    data object AliasInUse : MatrixRoomCreateData
}

data class MatrixRoomStateEventData(
    val type: String,
    val stateKey: String,
    val sender: String,
    val content: JsonNode,
)

enum class MatrixRoomStateType(val eventType: String) {
    POWER_LEVELS("m.room.power_levels"),
    ENCRYPTION("m.room.encryption"),
    JOIN_RULES("m.room.join_rules"),
    HISTORY_VISIBILITY("m.room.history_visibility"),
    GUEST_ACCESS("m.room.guest_access"),
}
