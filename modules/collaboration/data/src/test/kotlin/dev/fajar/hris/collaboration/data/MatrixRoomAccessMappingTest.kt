package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.MatrixRoomAccessDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomAccessRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixRoomAccessMappingTest {
    private val json = JsonMapper.builder().build()
    private val company = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val peer = UUID.randomUUID()
    private val owner = CollaborationSyncOwner(company, account, UUID.randomUUID(), "proof.invalid")
    private val room =
        CollaborationOwnedRoom(
            company,
            UUID.randomUUID(),
            account,
            peer,
            account,
            "proof.invalid",
            "!owned",
        )
    private val user = "@${collaborationMatrixLocalpart(company, account)}:proof.invalid"
    private val event =
        mapOf(
            "type" to "m.room.member",
            "state_key" to user,
            "sender" to user,
            "room_id" to room.roomId,
            "event_id" to "\$fixture",
            "origin_server_ts" to 1,
            "content" to mapOf("membership" to "join"),
        )
    private var reply: JsonNode = node(emptyMap<String, Any>())
    private val repository =
        InternalCollaborationMatrixRoomAccessRepository(
            object : MatrixRoomAccessDataSource {
                override fun join(roomId: String, token: String, deadline: Instant) = reply

                override fun receipt(
                    roomId: String,
                    receiptType: String,
                    eventId: String,
                    token: String,
                    deadline: Instant,
                ) = reply

                override fun stateEvents(roomId: String, token: String, deadline: Instant) = reply

                override fun memberEvents(
                    roomId: String,
                    at: String?,
                    token: String,
                    deadline: Instant,
                ) = reply
            }
        )

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    @Test
    fun receiptAcceptsOnlyEmptySuccessAndNeverReturnsProviderCredentials() {
        for (body in
            listOf<Any>(mapOf("access_token" to "private"), listOf("private"), "private")) {
            reply = node(body)
            assertEquals(
                "collaboration_provider_invalid_response",
                (repository.receipt(
                        room,
                        "m.read.private",
                        "\$event",
                        "fixture",
                        Instant.now().plusSeconds(5),
                    ) as Result.Failed)
                    .failure
                    .code,
            )
        }
        reply = node(emptyMap<String, Any>())
        for (type in listOf("m.read", "m.read.private")) {
            assertEquals(
                Result.Success(Unit),
                repository.receipt(room, type, "\$event", "fixture", Instant.now().plusSeconds(5)),
            )
        }
    }

    @Test
    fun joinCannotAdoptAnotherProviderRoomOrExtraCredentialFields() {
        for (body in
            listOf(
                mapOf("room_id" to "!foreign"),
                mapOf("room_id" to room.roomId, "access_token" to "private"),
            )) {
            reply = node(body)
            assertEquals(
                "collaboration_provider_invalid_response",
                (repository.join(room, "fixture", Instant.now().plusSeconds(5)) as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun roomReadsRejectForeignMembershipDuplicatesTimelineEventsAndMalformedShapes() {
        for (events in
            listOf(
                listOf(event + ("room_id" to "!foreign")),
                listOf(event + ("state_key" to "@foreign:proof.invalid")),
                listOf(event, event),
                listOf(event + ("type" to "m.room.encrypted")),
            )) {
            reply = node(events)
            assertInstanceOf(
                Result.Failed::class.java,
                repository.state(owner, room, "fixture", Instant.now().plusSeconds(5)),
            )
            reply = node(mapOf("chunk" to events))
            assertInstanceOf(
                Result.Failed::class.java,
                repository.members(owner, room, null, "fixture", Instant.now().plusSeconds(5)),
            )
        }
        reply = node(mapOf("chunk" to List(3) { event }))
        assertInstanceOf(
            Result.Failed::class.java,
            repository.members(owner, room, null, "fixture", Instant.now().plusSeconds(5)),
        )
        reply = node(mapOf("chunk" to listOf(event)))
        assertEquals(
            1,
            (repository.members(owner, room, null, "fixture", Instant.now().plusSeconds(5))
                    as Result.Success)
                .value
                .events
                .size,
        )
    }
}
