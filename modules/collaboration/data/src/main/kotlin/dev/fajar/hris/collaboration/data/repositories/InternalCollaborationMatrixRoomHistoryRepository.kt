package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomHistoryRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.JsonNode

class InternalCollaborationMatrixRoomHistoryRepository(
    private val source: MatrixRoomHistoryDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationMatrixRoomHistoryRepository {
    override fun messages(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        request: CollaborationRoomPageRequest,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        val from = request.from?.let { openMatrixRoomCursor(crypto, owner, room.roomId, it) }
        val to = request.to?.let { openMatrixRoomCursor(crypto, owner, room.roomId, it) }
        val raw =
            source.messages(
                room.roomId,
                from,
                to,
                request.direction,
                request.limit,
                bearer,
                deadline,
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
                    else sealMatrixRoomCursor(crypto, owner, room.roomId, start)),
                "chunk" to events(owner, room, chunk),
                "state" to state(owner, room, raw),
            )
        if (end != null && !stalled)
            output["end"] = sealMatrixRoomCursor(crypto, owner, room.roomId, end)
        CollaborationRoomHistory(output)
    }

    override fun context(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        limit: Int,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        val raw = source.context(room.roomId, eventId, limit, bearer, deadline)
        objectNode(raw)
        val before = array(raw, "events_before", limit)
        val after = array(raw, "events_after", limit)
        if (before.size + after.size > limit) throw MatrixResponseException()
        val target = raw.path("event")
        if (target.path("event_id").asString() != eventId) throw MatrixResponseException()
        val ids = mutableSetOf<String>()
        val output =
            linkedMapOf<String, Any>(
                "event" to event(owner, room, target).also { ids.add(eventId) },
                "events_before" to events(owner, room, before, ids),
                "events_after" to events(owner, room, after, ids),
                "state" to state(owner, room, raw),
            )
        for (name in listOf("start", "end")) position(raw, name)?.let {
            output[name] = sealMatrixRoomCursor(crypto, owner, room.roomId, it)
        }
        CollaborationRoomHistory(output)
    }

    override fun event(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        eventId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomHistory> = safeMatrixProviderCall {
        val raw = source.event(room.roomId, eventId, bearer, deadline)
        if (raw.path("event_id").asString() != eventId) throw MatrixResponseException()
        CollaborationRoomHistory(event(owner, room, raw))
    }

    private fun event(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        raw: JsonNode,
    ): Map<String, Any> {
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        return (mapCollaborationDirectRoomEvent(
            raw,
            setOf(user(room.firstAccountId), user(room.secondAccountId)),
            user(owner.accountId),
            room.roomId,
            user(room.creatorAccountId),
        ) ?: throw MatrixResponseException()) + ("room_id" to room.roomId)
    }

    private fun events(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        raw: List<JsonNode>,
        ids: MutableSet<String> = mutableSetOf(),
    ): List<Map<String, Any>> =
        raw.map {
            event(owner, room, it).also { mapped ->
                if (!ids.add(mapped["event_id"] as String)) throw MatrixResponseException()
            }
        }

    private fun state(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        raw: JsonNode,
    ): List<Map<String, Any>> {
        if (!raw.has("state")) return emptyList()
        val keys = mutableSetOf<Pair<String, String>>()
        return events(owner, room, array(raw, "state", 256)).onEach {
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
