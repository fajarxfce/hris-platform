package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixSpaceRoomAccessMappingTest {
    private val json = JsonMapper.builder().build()

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    private fun event(f: SpaceRoomHistoryFixture, user: String, type: String = "m.room.member") =
        mapOf(
            "type" to type,
            "state_key" to if (type == "m.room.create") "" else user,
            "sender" to user,
            "room_id" to f.roomId,
            "event_id" to "\$fixture-$type-$user",
            "origin_server_ts" to 1,
            "content" to
                if (type == "m.room.create") mapOf("room_version" to "12", "m.federate" to false)
                else mapOf("membership" to "join"),
        )

    private inner class Raw(val f: SpaceRoomHistoryFixture) : MatrixRoomAccessDataSource {
        var calls = 0
        var response = node(emptyMap<String, Any>())
        var failure: Exception? = null
        var position: String? = null
        var receiptType: String? = null
        var receiptEvent: String? = null

        private fun read(roomId: String, token: String, deadline: Instant): JsonNode {
            calls++
            assertEquals(f.roomId, roomId)
            assertEquals("fixture-provider", token)
            assertEquals(f.at.plusSeconds(30), deadline)
            failure?.let { throw it }
            return response
        }

        override fun join(roomId: String, token: String, deadline: Instant) =
            read(roomId, token, deadline)

        override fun receipt(
            roomId: String,
            receiptType: String,
            eventId: String,
            token: String,
            deadline: Instant,
        ): JsonNode {
            this.receiptType = receiptType
            receiptEvent = eventId
            return read(roomId, token, deadline)
        }

        override fun stateEvents(roomId: String, token: String, deadline: Instant) =
            read(roomId, token, deadline)

        override fun memberEvents(
            roomId: String,
            at: String?,
            token: String,
            deadline: Instant,
        ): JsonNode {
            position = at
            return read(roomId, token, deadline)
        }
    }

    @Test
    fun metadataUsesExactOriginalCreatorAndParticipantsAcrossFreshAndRetainedFrames() {
        for (legacy in listOf(false, true)) {
            val f = SpaceRoomHistoryFixture(legacy)
            val raw = Raw(f)
            val repository = InternalCollaborationSpaceMatrixRoomAccessRepository(raw)
            val original = if (legacy) f.original() else null
            val rooms =
                listOf(CollaborationSpaceReadRoom.Published(f.room, original)) +
                    listOfNotNull(original?.let { CollaborationSpaceReadRoom.Original(it) })
            for (room in rooms) {
                raw.response = node(mapOf("room_id" to f.roomId))
                assertEquals(
                    f.roomId,
                    success(
                        repository.join(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
                    ),
                )
                val creates = event(f, f.user(f.pair[1]), "m.room.create")
                val members = f.pair.map { event(f, f.user(it)) }
                raw.response = node(listOf(creates) + members)
                val state =
                    success(
                        repository.state(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
                    )
                assertEquals(
                    f.user(f.pair[1]),
                    state.events.single { it["type"] == "m.room.create" }["sender"],
                )
                assertTrue(state.events.all { it["room_id"] == f.roomId })
                raw.response = node(mapOf("chunk" to members))
                val read =
                    success(
                        repository.members(
                            f.owner,
                            room,
                            "original-private-position",
                            "fixture-provider",
                            f.at.plusSeconds(30),
                        )
                    )
                assertEquals(
                    setOf(f.user(f.pair[0]), f.user(f.pair[1])),
                    read.events.map { it["state_key"] }.toSet(),
                )
                assertEquals("original-private-position", raw.position)
                raw.response = node(emptyMap<String, Any>())
                for (type in listOf("m.read", "m.read.private")) {
                    success(
                        repository.receipt(
                            f.owner,
                            room,
                            type,
                            "\$original-event",
                            "fixture-provider",
                            f.at.plusSeconds(30),
                        )
                    )
                    assertEquals(type, raw.receiptType)
                    assertEquals("\$original-event", raw.receiptEvent)
                }
            }
        }
    }

    @Test
    fun invalidOwnerOrForeignOriginalTupleCannotReachAnyMetadataOperation() {
        val f = SpaceRoomHistoryFixture(true)
        val raw = Raw(f)
        val repository = InternalCollaborationSpaceMatrixRoomAccessRepository(raw)
        val original = f.original()
        val valid = CollaborationSpaceReadRoom.Published(f.room, original)
        val cases =
            listOf(
                f.owner.copy(accountId = UUID.randomUUID()) to valid,
                f.owner to
                    CollaborationSpaceReadRoom.Original(original.copy(spaceId = UUID.randomUUID())),
                f.owner to
                    valid.copy(
                        original =
                            original.copy(
                                conversation = original.conversation.copy(id = UUID.randomUUID())
                            )
                    ),
                f.owner to
                    valid.copy(
                        value =
                            f.room.copy(
                                binding = f.room.binding.copy(conversationId = UUID.randomUUID())
                            )
                    ),
            )
        for ((owner, room) in cases) {
            assertInstanceOf(
                Result.Failed::class.java,
                repository.join(owner, room, "fixture-provider", f.at.plusSeconds(30)),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                repository.receipt(
                    owner,
                    room,
                    "m.read.private",
                    "\$event",
                    "fixture-provider",
                    f.at.plusSeconds(30),
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                repository.state(owner, room, "fixture-provider", f.at.plusSeconds(30)),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                repository.members(owner, room, null, "fixture-provider", f.at.plusSeconds(30)),
            )
        }
        assertEquals(0, raw.calls)
    }

    @Test
    fun malformedProviderMetadataCannotAdoptAnotherRoomOrLeakPlaintextAndCancellationPropagates() {
        val f = SpaceRoomHistoryFixture()
        val raw = Raw(f)
        val repository = InternalCollaborationSpaceMatrixRoomAccessRepository(raw)
        val room = CollaborationSpaceReadRoom.Published(f.room)
        for (response in
            listOf(
                mapOf("room_id" to "!foreign"),
                mapOf("room_id" to f.roomId, "access_token" to "private"),
            )) {
            raw.response = node(response)
            assertEquals(
                "collaboration_provider_invalid_response",
                (repository.join(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        val member = event(f, f.user(f.pair[0]))
        for (events in
            listOf(
                listOf(member, member),
                listOf(member + ("state_key" to "@foreign:example.test")),
                listOf(member + ("type" to "m.room.encrypted")),
                List(257) { member },
            )) {
            raw.response = node(events)
            assertEquals(
                "collaboration_provider_invalid_response",
                (repository.state(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
                        as Result.Failed)
                    .failure
                    .code,
            )
        }
        raw.response = node(mapOf("access_token" to "private"))
        assertEquals(
            "collaboration_provider_invalid_response",
            (repository.receipt(
                    f.owner,
                    room,
                    "m.read.private",
                    "\$event",
                    "fixture-provider",
                    f.at.plusSeconds(30),
                ) as Result.Failed)
                .failure
                .code,
        )
        raw.failure = MatrixStatusException(503)
        assertEquals(
            "collaboration_provider_unavailable",
            (repository.members(f.owner, room, null, "fixture-provider", f.at.plusSeconds(30))
                    as Result.Failed)
                .failure
                .code,
        )
        raw.failure = CancellationException("fictional metadata cancellation")
        assertThrows(CancellationException::class.java) {
            repository.state(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
        }
        raw.failure = InterruptedException("fictional metadata interruption")
        assertThrows(InterruptedException::class.java) {
            repository.join(f.owner, room, "fixture-provider", f.at.plusSeconds(30))
        }
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }
}
