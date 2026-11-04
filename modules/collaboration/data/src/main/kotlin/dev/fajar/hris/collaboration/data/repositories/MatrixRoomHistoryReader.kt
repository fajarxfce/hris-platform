package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import java.time.Instant
import tools.jackson.databind.JsonNode

/** Protocol acquisition/projection only; callers supply already admitted framing. */
internal class MatrixRoomHistoryReader(private val source: MatrixRoomHistoryDataSource) {
    fun messages(
        frame: MatrixRoomHistoryFrame,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): CollaborationRoomHistory {
        val from = request.from?.let { frame.cursors.open(it) }
        val to = request.to?.let { frame.cursors.open(it) }
        val raw =
            source.messages(
                frame.roomId,
                from,
                to,
                request.direction,
                request.limit,
                bearer,
                deadline,
                request.filter.toMatrixFilter(),
            )
        objectNode(raw)
        val chunk = array(raw, "chunk", request.limit)
        val start = position(raw, "start") ?: throw MatrixResponseException()
        val end = position(raw, "end")
        // Compare private positions before randomized encryption. Empty pages may still
        // advance across visibility gaps; nonempty repeated positions cannot be retried.
        val stalled = end != null && (end == start || end == from)
        if (stalled && chunk.isNotEmpty()) throw MatrixResponseException()
        val output =
            linkedMapOf<String, Any>(
                "start" to
                    (if (start == from) requireNotNull(request.from)
                    else frame.cursors.seal(start)),
                "chunk" to events(frame, chunk),
                "state" to state(frame, raw),
            )
        if (end != null && !stalled) output["end"] = frame.cursors.seal(end)
        return CollaborationRoomHistory(output)
    }

    fun context(
        frame: MatrixRoomHistoryFrame,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
        filter: CollaborationRoomHistoryFilter,
    ): CollaborationRoomHistory {
        val raw =
            source.context(frame.roomId, eventId, limit, bearer, deadline, filter.toMatrixFilter())
        objectNode(raw)
        val before = array(raw, "events_before", limit)
        val after = array(raw, "events_after", limit)
        if (before.size + after.size > limit) throw MatrixResponseException()
        val target = raw.path("event")
        if (target.path("event_id").asString() != eventId) throw MatrixResponseException()
        val ids = mutableSetOf<String>()
        val output =
            linkedMapOf<String, Any>(
                "event" to event(frame, target).also { ids.add(eventId) },
                "events_before" to events(frame, before, ids),
                "events_after" to events(frame, after, ids),
                "state" to state(frame, raw),
            )
        for (name in listOf("start", "end")) position(raw, name)?.let {
            output[name] = frame.cursors.seal(it)
        }
        return CollaborationRoomHistory(output)
    }

    fun event(
        frame: MatrixRoomHistoryFrame,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): CollaborationRoomHistory {
        val raw = source.event(frame.roomId, eventId, bearer, deadline)
        if (raw.path("event_id").asString() != eventId) throw MatrixResponseException()
        return CollaborationRoomHistory(event(frame, raw))
    }

    private fun event(frame: MatrixRoomHistoryFrame, raw: JsonNode): Map<String, Any> {
        return (mapCollaborationDirectRoomEvent(
            raw,
            frame.users,
            frame.currentUser,
            frame.roomId,
            frame.creatorUser,
        ) ?: throw MatrixResponseException()) + ("room_id" to frame.roomId)
    }

    private fun events(
        frame: MatrixRoomHistoryFrame,
        raw: List<JsonNode>,
        ids: MutableSet<String> = mutableSetOf(),
    ): List<Map<String, Any>> =
        raw.map {
            event(frame, it).also { mapped ->
                if (!ids.add(mapped["event_id"] as String)) throw MatrixResponseException()
            }
        }

    private fun state(frame: MatrixRoomHistoryFrame, raw: JsonNode): List<Map<String, Any>> {
        if (!raw.has("state")) return emptyList()
        val keys = mutableSetOf<Pair<String, String>>()
        return events(frame, array(raw, "state", 256)).onEach {
            if (it["type"] in setOf("m.room.encrypted", "m.room.redaction", "m.room.message"))
                throw MatrixResponseException()
            val key = it["state_key"] as? String ?: throw MatrixResponseException()
            if (!keys.add(it["type"] as String to key)) throw MatrixResponseException()
        }
    }

    private fun array(raw: JsonNode, name: String, limit: Int): List<JsonNode> {
        val value = raw.path(name)
        if (!value.isArray || value.size() > limit) throw MatrixResponseException()
        return value.toList()
    }

    private fun position(raw: JsonNode, name: String): String? {
        val value = raw.path(name)
        if (value.isMissingNode) return null
        if (
            !value.isString ||
                value.asString().length !in 1..1024 ||
                value.asString().any { it.code !in 33..126 }
        )
            throw MatrixResponseException()
        return value.asString()
    }

    private fun objectNode(raw: JsonNode) {
        if (!raw.isObject) throw MatrixResponseException()
    }
}

internal interface MatrixRoomHistoryCursors {
    fun open(cursor: String): String

    fun seal(position: String): String
}

internal data class MatrixRoomHistoryFrame(
    val roomId: String,
    val users: Set<String>,
    val currentUser: String,
    val creatorUser: String,
    val cursors: MatrixRoomHistoryCursors,
)
