package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixRoomHistoryRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.IdentityKeyUnavailableException
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixSpaceOriginalRoomHistoryTest {
    private val f = SpaceRoomHistoryFixture(legacy = true)
    private val original = f.original()
    private val binding = requireNotNull(original.room)
    private val crypto = collaborationCursorFixture()
    private val json = JsonMapper.builder().build()
    private val deadline = f.at.plusSeconds(30)
    private val request = CollaborationRoomPageRequest(null, null, "b", 10)
    private val freshId = UUID.randomUUID()
    private val fresh =
        f.owner.copy(
            deviceId = freshId,
            matrixDeviceId = "HRISG_${freshId.toString().replace("-", "")}",
            legacy = null,
        )
    private var reply: JsonNode = node(emptyMap<String, Any>())
    private var calls = 0
    private var from: String? = null
    private var failure: Exception? = null
    private var filters = emptyMap<String, Boolean>()
    private val source =
        object : MatrixRoomHistoryDataSource {
            private fun read(roomId: String, token: String, deadline: Instant): JsonNode {
                calls++
                assertEquals(f.roomId, roomId)
                assertEquals("fixture-original-provider", token)
                assertEquals(this@MatrixSpaceOriginalRoomHistoryTest.deadline, deadline)
                failure?.let { throw it }
                return reply
            }

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
                this@MatrixSpaceOriginalRoomHistoryTest.from = from
                filters = filter
                return read(roomId, token, deadline)
            }

            override fun context(
                roomId: String,
                eventId: String,
                limit: Int,
                token: String,
                deadline: Instant,
                filter: Map<String, Boolean>,
            ): JsonNode {
                filters = filter
                return read(roomId, token, deadline)
            }

            override fun event(
                roomId: String,
                eventId: String,
                token: String,
                deadline: Instant,
            ): JsonNode = read(roomId, token, deadline)
        }
    private val repository = InternalCollaborationSpaceMatrixRoomHistoryRepository(source, crypto)
    private val event =
        mapOf(
            "type" to "m.room.encrypted",
            "sender" to f.user(f.pair[1]),
            "event_id" to "\$original",
            "room_id" to f.roomId,
            "origin_server_ts" to 1,
            "content" to
                mapOf(
                    "algorithm" to "m.megolm.v1.aes-sha2",
                    "ciphertext" to "opaque-original",
                    "device_id" to "ORIGINAL_DEVICE",
                ),
        )

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    private fun payload(value: Result<CollaborationRoomHistory>): Map<String, Any> {
        assertInstanceOf(
            Result.Success::class.java,
            value,
            (value as? Result.Failed)?.failure?.code,
        )
        return (value as Result.Success).value.payload
    }

    private fun messages(
        owner: CollaborationSpaceHistoryOwner = f.owner,
        room: CollaborationSpaceLegacyConversation = original,
        request: CollaborationRoomPageRequest = this.request,
    ) = repository.messages(owner, room, request, "fixture-original-provider", deadline)

    private fun invalid(block: () -> String) =
        assertEquals(
            MatrixRoomCursorFailure.INVALID,
            assertThrows(MatrixRoomCursorException::class.java) { block() }.reason,
        )

    @Test
    fun originalAndPublishedRoomShareGroupCursorsButOnlyRetainedDeviceBridgesCompanyCursor() {
        val old = CollaborationSyncOwner(f.company, f.pair[0], f.originalDevice, f.server)
        val oldCursor = sealMatrixRoomCursor(crypto, old, f.roomId, "old")
        assertEquals("old", openMatrixSpaceRoomCursor(crypto, f.owner, original, oldCursor))
        invalid { openMatrixSpaceRoomCursor(crypto, fresh, original, oldCursor) }
        for (owner in listOf(f.owner, fresh)) {
            val before = sealMatrixSpaceRoomCursor(crypto, owner, original, "before")
            assertEquals("before", openMatrixSpaceRoomCursor(crypto, owner, f.room, before))
            val after = sealMatrixSpaceRoomCursor(crypto, owner, f.room, "after")
            assertEquals("after", openMatrixSpaceRoomCursor(crypto, owner, original, after))
            invalid { openMatrixRoomCursor(crypto, old, f.roomId, before) }
            val another = original.copy(room = binding.copy(roomId = "!another:${f.server}"))
            invalid { openMatrixSpaceRoomCursor(crypto, owner, another, before) }
            val other = if (owner == fresh) f.owner else fresh
            invalid { openMatrixSpaceRoomCursor(crypto, other, original, before) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            openMatrixSpaceRoomCursor(
                crypto,
                f.owner,
                original.copy(
                    conversation = original.conversation.copy(companyId = UUID.randomUUID())
                ),
                oldCursor,
            )
        }
    }

    @Test
    fun originalCursorKeyFailureAndCancellationNeverFallBackOrLoseClassification() {
        var decryptions = 0
        var error: Exception = IdentityKeyUnavailableException()
        val unavailable =
            object : IdentityTokenDataSource by crypto {
                override fun decrypt(purpose: String, value: String): String {
                    decryptions++
                    throw error
                }

                override fun encrypt(purpose: String, value: String): String = throw error
            }
        assertEquals(
            MatrixRoomCursorFailure.RESET_REQUIRED,
            assertThrows(MatrixRoomCursorException::class.java) {
                    openMatrixSpaceRoomCursor(unavailable, f.owner, original, "fixture")
                }
                .reason,
        )
        assertEquals(1, decryptions)
        assertEquals(
            MatrixRoomCursorFailure.UNAVAILABLE,
            assertThrows(MatrixRoomCursorException::class.java) {
                    sealMatrixSpaceRoomCursor(unavailable, f.owner, original, "t10")
                }
                .reason,
        )
        error = CancellationException("fictional cancellation")
        assertThrows(CancellationException::class.java) {
            openMatrixSpaceRoomCursor(unavailable, f.owner, original, "fixture")
        }
        assertEquals(2, decryptions)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) {
                openMatrixSpaceRoomCursor(unavailable, f.owner, original, "fixture")
            }
            assertEquals(2, decryptions)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun originalFramingKeepsActualCreatorCiphertextAndAllThreeReadShapes() {
        assertNotEquals(original.conversation.createdBy, binding.createdBy)
        val creation =
            mapOf(
                "type" to "m.room.create",
                "event_id" to "\$creation",
                "sender" to f.user(binding.createdBy),
                "room_id" to f.roomId,
                "origin_server_ts" to 0,
                "state_key" to "",
                "content" to
                    mapOf(
                        "creator" to f.user(binding.createdBy),
                        "room_version" to "12",
                        "m.federate" to false,
                    ),
            )
        val cursor = sealMatrixSpaceRoomCursor(crypto, f.owner, original, "t10")
        reply =
            node(
                mapOf(
                    "start" to "t10",
                    "end" to "t9",
                    "chunk" to listOf(event),
                    "state" to listOf(creation),
                    "access_token" to "do-not-forward",
                )
            )
        val page =
            payload(
                messages(
                    request =
                        request.copy(
                            from = cursor,
                            filter = CollaborationRoomHistoryFilter(true, true),
                        )
                )
            )
        assertEquals("t10", from)
        assertEquals(cursor, page["start"])
        assertEquals(
            "t9",
            openMatrixSpaceRoomCursor(crypto, f.owner, f.room, page["end"] as String),
        )
        assertEquals(
            event["content"],
            ((page["chunk"] as List<*>).single() as Map<*, *>)["content"],
        )
        assertEquals(
            f.user(binding.createdBy),
            ((page["state"] as List<*>).single() as Map<*, *>)["sender"],
        )
        assertFalse(page.containsKey("access_token"))
        assertEquals(
            mapOf("lazy_load_members" to true, "include_redundant_members" to true),
            filters,
        )
        reply =
            node(
                mapOf(
                    "event" to event,
                    "events_before" to emptyList<Any>(),
                    "events_after" to emptyList<Any>(),
                    "end" to "t8",
                )
            )
        val context =
            payload(
                repository.context(
                    fresh,
                    original,
                    "\$original",
                    0,
                    "fixture-original-provider",
                    deadline,
                    CollaborationRoomHistoryFilter(true, true),
                )
            )
        assertEquals(
            "t8",
            openMatrixSpaceRoomCursor(crypto, fresh, f.room, context["end"] as String),
        )
        assertEquals("\$original", (context["event"] as Map<*, *>)["event_id"])
        reply = node(event)
        assertEquals(
            event["content"],
            payload(
                repository.event(
                    fresh,
                    original,
                    "\$original",
                    "fixture-original-provider",
                    deadline,
                )
            )["content"],
        )
        assertEquals(3, calls)
    }

    @Test
    fun malformedOriginalOwnershipAndForeignCursorFailBeforeProviderIo() {
        for (room in
            listOf(
                original.copy(spaceId = UUID.randomUUID()),
                original.copy(room = null),
                original.copy(
                    conversation = original.conversation.copy(companyId = UUID.randomUUID())
                ),
                original.copy(room = binding.copy(createdBy = UUID.randomUUID())),
                original.copy(room = binding.copy(boundAt = deadline.plusSeconds(1))),
            )) assertEquals(
            "collaboration_provider_invalid_response",
            (messages(room = room) as Result.Failed).failure.code,
        )
        val foreign = sealMatrixSpaceRoomCursor(crypto, fresh, original, "t10")
        assertEquals(
            "invalid_collaboration_history_cursor",
            (messages(request = request.copy(from = foreign)) as Result.Failed).failure.code,
        )
        assertEquals(0, calls)
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to emptyList<Any>()))
        assertFalse(payload(messages()).containsKey("end"))
        reply = node(mapOf("start" to "t10", "end" to "t10", "chunk" to listOf(event)))
        assertEquals(
            "collaboration_provider_invalid_response",
            (messages() as Result.Failed).failure.code,
        )
        for (value in
            listOf(
                event + ("sender" to "@foreign:${f.server}"),
                event + ("type" to "m.room.message"),
            )) {
            reply = node(mapOf("start" to "t10", "chunk" to listOf(value)))
            assertEquals(
                "collaboration_provider_invalid_response",
                (messages() as Result.Failed).failure.code,
            )
        }
        failure = CancellationException("fictional provider cancellation")
        assertThrows(CancellationException::class.java) { messages() }
        failure = MatrixStatusException(503)
        assertEquals(
            "collaboration_provider_unavailable",
            (messages() as Result.Failed).failure.code,
        )
    }
}
