package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixRoomHistoryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixSpaceRoomHistoryMappingTest {
    private val json = JsonMapper.builder().build()
    private val fixture = SpaceRoomHistoryFixture()
    private val owner = fixture.owner
    private val room = fixture.room
    private val user = owner.matrixUserId
    private val event =
        mapOf(
            "type" to "m.room.encrypted",
            "sender" to user,
            "event_id" to "\$fixture",
            "room_id" to room.binding.roomId,
            "origin_server_ts" to 1,
            "content" to mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque"),
        )
    private val crypto = collaborationCursorFixture()
    private var reply: JsonNode = node(emptyMap<String, Any>())
    private var calls = 0
    private var observedFrom: String? = null
    private var observedRoom: String? = null
    private var observedFilter = emptyMap<String, Boolean>()
    private val repository =
        InternalCollaborationSpaceMatrixRoomHistoryRepository(
            object : MatrixRoomHistoryDataSource {
                override fun messages(
                    roomId: String,
                    from: String?,
                    to: String?,
                    direction: String,
                    limit: Int,
                    token: String,
                    deadline: Instant,
                    filter: Map<String, Boolean>,
                ): JsonNode {
                    calls++
                    observedRoom = roomId
                    observedFrom = from
                    observedFilter = filter
                    return reply
                }

                override fun context(
                    roomId: String,
                    eventId: String,
                    limit: Int,
                    token: String,
                    deadline: Instant,
                    filter: Map<String, Boolean>,
                ): JsonNode {
                    calls++
                    observedRoom = roomId
                    observedFilter = filter
                    return reply
                }

                override fun event(
                    roomId: String,
                    eventId: String,
                    token: String,
                    deadline: Instant,
                ): JsonNode {
                    calls++
                    observedRoom = roomId
                    return reply
                }
            },
            crypto,
        )

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    @Test
    fun membershipLoadingFlagsReachBothProviderReadsWithoutChangingRoomFraming() {
        val filter = CollaborationRoomHistoryFilter(true, true)
        reply = node(mapOf("start" to "t10", "chunk" to listOf(event)))
        assertInstanceOf(
            Result.Success::class.java,
            repository.messages(
                owner,
                room,
                CollaborationRoomPageRequest(null, null, "b", 10, filter),
                "fixture",
                Instant.now().plusSeconds(5),
            ),
        )
        assertEquals(
            mapOf("lazy_load_members" to true, "include_redundant_members" to true),
            observedFilter,
        )
        reply =
            node(
                mapOf(
                    "event" to event,
                    "events_before" to emptyList<Any>(),
                    "events_after" to emptyList<Any>(),
                )
            )
        assertInstanceOf(
            Result.Success::class.java,
            repository.context(
                owner,
                room,
                "\$fixture",
                10,
                "fixture",
                Instant.now().plusSeconds(5),
                filter,
            ),
        )
        assertEquals(
            mapOf("lazy_load_members" to true, "include_redundant_members" to true),
            observedFilter,
        )
        val before = calls
        assertInstanceOf(
            Result.Failed::class.java,
            repository.context(
                owner,
                room,
                "\$fixture",
                10,
                "fixture",
                Instant.now().plusSeconds(5),
                CollaborationRoomHistoryFilter(false, true),
            ),
        )
        assertEquals(before, calls)
    }

    private fun messages(from: String? = null, limit: Int = 10) =
        repository.messages(
            owner,
            room,
            CollaborationRoomPageRequest(from, null, "b", limit),
            "fixture",
            Instant.now().plusSeconds(5),
        )

    private fun payload(result: Result<CollaborationRoomHistory>) =
        (result.also { assertInstanceOf(Result.Success::class.java, it) } as Result.Success)
            .value
            .payload

    @Test
    fun cursorsAreOpenedBeforeIoAndProgressIsComparedBeforeRandomizedEncryption() {
        val cursor = sealMatrixSpaceRoomCursor(crypto, owner, room, "t10")
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to emptyList<Any>()))
        assertFalse(payload(messages(cursor)).containsKey("end"))
        assertEquals("t10", observedFrom)
        reply = node(mapOf("start" to "t10", "end" to "t9", "chunk" to emptyList<Any>()))
        val advancing = payload(messages(cursor))
        assertEquals(cursor, advancing["start"])
        assertEquals(
            "t9",
            openMatrixSpaceRoomCursor(crypto, owner, room, advancing["end"] as String),
        )
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to listOf(event)))
        assertInstanceOf(Result.Failed::class.java, messages(cursor))
        val before = calls
        val foreign =
            sealMatrixSpaceRoomCursor(
                crypto,
                owner,
                room.copy(binding = room.binding.copy(roomId = "!other")),
                "t10",
            )
        assertEquals(
            "invalid_collaboration_history_cursor",
            (messages(foreign) as Result.Failed).failure.code,
        )
        assertEquals(before, calls)
    }

    @Test
    fun pagesRejectForeignEventsDuplicatesOversizeMalformedAndPlaintext() {
        for (events in
            listOf(
                listOf(event + ("room_id" to "!foreign")),
                listOf(event + ("sender" to "@foreign:proof.invalid")),
                listOf(event, event),
                listOf(event + ("type" to "m.room.message")),
                List(11) { event },
            )) {
            reply = node(mapOf("start" to "t10", "end" to "t9", "chunk" to events))
            assertInstanceOf(Result.Failed::class.java, messages())
        }
        reply =
            node(
                mapOf(
                    "start" to "t10",
                    "end" to "t9",
                    "chunk" to listOf(event),
                    "state" to listOf(event),
                )
            )
        assertInstanceOf(Result.Failed::class.java, messages())
        reply =
            node(
                mapOf("start" to "t10", "chunk" to listOf(event), "access_token" to "never-forward")
            )
        val accepted = payload(messages())
        assertFalse(accepted.containsKey("access_token"))
        assertFalse(accepted.containsKey("end"))
        assertEquals(1, (accepted["chunk"] as List<*>).size)
    }

    @Test
    fun contextAndSingleEventValidateTargetAndShareRoomFraming() {
        fun context(limit: Int = 10) =
            repository.context(
                owner,
                room,
                "\$fixture",
                limit,
                "fixture",
                Instant.now().plusSeconds(5),
            )
        reply =
            node(
                mapOf(
                    "event" to event,
                    "events_before" to emptyList<Any>(),
                    "events_after" to listOf(event + ("event_id" to "\$next")),
                    "start" to "t1",
                    "end" to "t3",
                )
            )
        val mapped = payload(context())
        assertEquals(
            "t1",
            openMatrixSpaceRoomCursor(crypto, owner, room, mapped["start"] as String),
        )
        assertInstanceOf(Result.Failed::class.java, context(0))
        reply =
            node(
                mapOf(
                    "event" to event,
                    "events_before" to listOf(event),
                    "events_after" to emptyList<Any>(),
                )
            )
        assertInstanceOf(Result.Failed::class.java, context())
        reply = node(event)
        assertEquals(
            room.binding.roomId,
            payload(
                repository.event(owner, room, "\$fixture", "fixture", Instant.now().plusSeconds(5))
            )["room_id"],
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.event(owner, room, "\$different", "fixture", Instant.now().plusSeconds(5)),
        )
    }

    @Test
    fun retainedAndMixedNamespacesKeepOriginalUsersCreatorAndLegacyCursorProgress() {
        val f = SpaceRoomHistoryFixture(legacy = true)
        val otherCompany = UUID.randomUUID()
        val mixed =
            f.room.copy(
                intent =
                    f.room.intent.copy(
                        origin = CollaborationSpaceRoomOrigin.SPACE,
                        original = null,
                        secondMatrixUserId =
                            "@${collaborationMatrixLocalpart(otherCompany, f.pair[1])}:${f.server}",
                    )
            )
        for (bound in listOf(f.room, mixed)) {
            val original =
                dev.fajar.hris.collaboration.domain.policies.collaborationSpaceLegacyHistoryOwner(
                    f.owner,
                    bound,
                )
            val from =
                if (original == null) sealMatrixSpaceRoomCursor(crypto, f.owner, bound, "t10")
                else sealMatrixRoomCursor(crypto, original, f.roomId, "t10")
            val own =
                event +
                    mapOf(
                        "sender" to f.owner.matrixUserId,
                        "room_id" to f.roomId,
                        "unsigned" to
                            mapOf(
                                "transaction_id" to "original-sdk-transaction",
                                "private" to "omit",
                            ),
                        "content" to
                            mapOf(
                                "algorithm" to "m.megolm.v1.aes-sha2",
                                "ciphertext" to "original-ciphertext",
                                "session_id" to "original-session",
                                "device_id" to f.owner.matrixDeviceId,
                            ),
                    )
            val peer =
                own + mapOf("sender" to bound.intent.secondMatrixUserId, "event_id" to "\$peer")
            val creation =
                mapOf(
                    "type" to "m.room.create",
                    "sender" to bound.intent.secondMatrixUserId,
                    "state_key" to "",
                    "room_id" to f.roomId,
                    "event_id" to "\$create",
                    "origin_server_ts" to 1,
                    "content" to mapOf("room_version" to "12", "m.federate" to false),
                )
            reply =
                node(
                    mapOf(
                        "start" to "t10",
                        "end" to "t9",
                        "chunk" to listOf(own, peer),
                        "state" to listOf(creation),
                    )
                )
            val output =
                payload(
                    repository.messages(
                        f.owner,
                        bound,
                        CollaborationRoomPageRequest(from, null, "b", 10),
                        "fixture",
                        Instant.now().plusSeconds(5),
                    )
                )
            assertEquals(f.roomId, observedRoom)
            assertEquals("t10", observedFrom)
            assertEquals(from, output["start"])
            val end = output["end"] as String
            assertEquals("t9", openMatrixSpaceRoomCursor(crypto, f.owner, bound, end))
            if (original != null)
                assertThrows(MatrixRoomCursorException::class.java) {
                    openMatrixRoomCursor(crypto, original, f.roomId, end)
                }
            val events = output["chunk"] as List<*>
            assertEquals(f.owner.matrixUserId, (events[0] as Map<*, *>)["sender"])
            assertEquals(own["content"], (events[0] as Map<*, *>)["content"])
            assertEquals(
                mapOf("transaction_id" to "original-sdk-transaction"),
                (events[0] as Map<*, *>)["unsigned"],
            )
            assertFalse((events[1] as Map<*, *>).containsKey("unsigned"))
            assertEquals(
                bound.intent.secondMatrixUserId,
                ((output["state"] as List<*>).single() as Map<*, *>)["sender"],
            )
        }
    }
}
