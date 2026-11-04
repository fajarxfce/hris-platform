package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationRoomEvents
import java.time.Instant
import tools.jackson.databind.JsonNode

/** Fixed metadata protocol; admission and exact room framing belong to callers. */
internal class MatrixRoomAccessReader(private val source: MatrixRoomAccessDataSource) {
    fun join(roomId: String, bearer: String, deadline: Instant): String {
        val response = source.join(roomId, bearer, deadline)
        if (
            !response.isObject ||
                response.path("room_id").asString() != roomId ||
                response.propertyNames() != setOf("room_id")
        )
            throw MatrixResponseException()
        return roomId
    }

    fun receipt(
        roomId: String,
        receiptType: String,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ) {
        require(receiptType in setOf("m.read", "m.read.private"))
        require(
            eventId.length in 2..255 &&
                eventId.startsWith('$') &&
                eventId.all { it.code in 33..126 }
        )
        val response = source.receipt(roomId, receiptType, eventId, bearer, deadline)
        if (!response.isObject || !response.isEmpty) throw MatrixResponseException()
    }

    fun state(
        frame: MatrixRoomAccessFrame,
        bearer: String,
        deadline: Instant,
    ): CollaborationRoomEvents {
        return events(
            frame,
            source.stateEvents(frame.roomId, bearer, deadline),
            membersOnly = false,
        )
    }

    fun members(
        frame: MatrixRoomAccessFrame,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): CollaborationRoomEvents {
        val response = source.memberEvents(frame.roomId, at, bearer, deadline)
        if (!response.isObject) throw MatrixResponseException()
        return events(frame, response.path("chunk"), membersOnly = true)
    }

    private fun events(
        frame: MatrixRoomAccessFrame,
        values: JsonNode,
        membersOnly: Boolean,
    ): CollaborationRoomEvents {
        if (!values.isArray || values.size() > (if (membersOnly) 2 else 256))
            throw MatrixResponseException()
        val pair = frame.users
        val keys = mutableSetOf<Pair<String, String>>()
        val output =
            values.mapNotNull { raw ->
                if (
                    !raw.isObject ||
                        !raw.path("type").isString ||
                        !raw.path("state_key").isString ||
                        raw.path("type").asString() in
                            setOf("m.room.encrypted", "m.room.redaction", "m.room.message") ||
                        (membersOnly && raw.path("type").asString() != "m.room.member")
                )
                    throw MatrixResponseException()
                if (!keys.add(raw.path("type").asString() to raw.path("state_key").asString()))
                    throw MatrixResponseException()
                mapCollaborationDirectRoomEvent(
                        raw,
                        pair,
                        frame.currentUser,
                        frame.roomId,
                        frame.creatorUser,
                    )
                    ?.plus("room_id" to frame.roomId)
            }
        return CollaborationRoomEvents(output)
    }
}

internal data class MatrixRoomAccessFrame(
    val roomId: String,
    val users: Set<String>,
    val currentUser: String,
    val creatorUser: String,
)
