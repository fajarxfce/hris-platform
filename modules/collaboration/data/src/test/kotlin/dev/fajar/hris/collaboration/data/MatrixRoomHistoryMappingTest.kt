package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomHistoryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixLocalpart
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixRoomHistoryMappingTest {
    private val json = JsonMapper.builder().build()
    private val owner =
        CollaborationSyncOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "proof.invalid",
        )
    private val room =
        CollaborationOwnedRoom(
            owner.companyId,
            UUID.randomUUID(),
            owner.accountId,
            UUID.randomUUID(),
            owner.accountId,
            owner.serverName,
            "!owned",
        )
    private val user =
        "@${collaborationMatrixLocalpart(owner.companyId, owner.accountId)}:${owner.serverName}"
    private val event =
        mapOf(
            "type" to "m.room.encrypted",
            "sender" to user,
            "event_id" to "\$fixture",
            "room_id" to room.roomId,
            "origin_server_ts" to 1,
            "content" to mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque"),
        )
    private val crypto = collaborationCursorFixture()
    private var reply: JsonNode = node(emptyMap<String, Any>())
    private var calls = 0
    private var observedFrom: String? = null
    private var observedFilter = emptyMap<String, Boolean>()
    private val repository =
        InternalCollaborationMatrixRoomHistoryRepository(
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
        val cursor = sealMatrixRoomCursor(crypto, owner, room.roomId, "t10")
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to emptyList<Any>()))
        assertFalse(payload(messages(cursor)).containsKey("end"))
        assertEquals("t10", observedFrom)
        reply = node(mapOf("start" to "t10", "end" to "t9", "chunk" to emptyList<Any>()))
        val advancing = payload(messages(cursor))
        assertEquals(cursor, advancing["start"])
        assertEquals(
            "t9",
            openMatrixRoomCursor(crypto, owner, room.roomId, advancing["end"] as String),
        )
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to listOf(event)))
        assertInstanceOf(Result.Failed::class.java, messages(cursor))
        val before = calls
        val foreign = sealMatrixRoomCursor(crypto, owner, "!other", "t10")
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
            openMatrixRoomCursor(crypto, owner, room.roomId, mapped["start"] as String),
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
            room.roomId,
            payload(
                repository.event(owner, room, "\$fixture", "fixture", Instant.now().plusSeconds(5))
            )["room_id"],
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repository.event(owner, room, "\$different", "fixture", Instant.now().plusSeconds(5)),
        )
    }
}
