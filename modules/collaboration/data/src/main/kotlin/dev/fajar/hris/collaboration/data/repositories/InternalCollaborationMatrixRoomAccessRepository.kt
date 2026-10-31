package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomAccessRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.JsonNode

class InternalCollaborationMatrixRoomAccessRepository(
    private val source: MatrixRoomAccessDataSource
) : CollaborationMatrixRoomAccessRepository {
    override fun join(
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<String> = safeMatrixProviderCall {
        val response = source.join(room.roomId, bearer, deadline)
        if (
            !response.isObject ||
                response.path("room_id").asString() != room.roomId ||
                response.propertyNames() != setOf("room_id")
        )
            throw MatrixResponseException()
        room.roomId
    }

    override fun state(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        events(owner, room, source.stateEvents(room.roomId, bearer, deadline), membersOnly = false)
    }

    override fun members(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        at: String?,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationRoomEvents> = safeMatrixProviderCall {
        val response = source.memberEvents(room.roomId, at, bearer, deadline)
        if (!response.isObject) throw MatrixResponseException()
        events(owner, room, response.path("chunk"), membersOnly = true)
    }

    private fun events(
        owner: CollaborationSyncOwner,
        room: CollaborationOwnedRoom,
        values: JsonNode,
        membersOnly: Boolean,
    ): CollaborationRoomEvents {
        if (!values.isArray || values.size() > (if (membersOnly) 2 else 256))
            throw MatrixResponseException()
        fun user(account: UUID) =
            "@${collaborationMatrixLocalpart(owner.companyId, account)}:${owner.serverName}"
        val pair = setOf(user(room.firstAccountId), user(room.secondAccountId))
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
                        user(owner.accountId),
                        room.roomId,
                        user(room.creatorAccountId),
                    )
                    ?.plus("room_id" to room.roomId)
            }
        return CollaborationRoomEvents(output)
    }
}
