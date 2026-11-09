package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixSyncRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Explicit original/current room framing; provider filters do not grant room authority. */
class MatrixSpaceSyncProjectionTest {
    private val f = SpaceRoomHistoryFixture(legacy = true)
    private val original = f.original()
    private val newConversation = UUID.randomUUID()
    private val newPeer = "@${collaborationSpaceMatrixLocalpart(f.space, f.pair[1])}:${f.server}"
    private val group =
        f.room.copy(
            intent =
                f.room.intent.copy(
                    conversationId = newConversation,
                    origin = CollaborationSpaceRoomOrigin.SPACE,
                    original = null,
                    secondMatrixUserId = newPeer,
                ),
            binding =
                f.room.binding.copy(
                    conversationId = newConversation,
                    roomId = "!current:${f.server}",
                ),
        )
    private val owner = f.owner
    private val oldPeer = f.user(f.pair[1])
    private val crypto = collaborationCursorFixture()
    private val json = JsonMapper.builder().build()
    private val deadline = f.at.plusSeconds(30)

    private fun scope(
        groups: List<CollaborationSpaceBoundRoom> = listOf(group),
        originals: List<CollaborationSpaceLegacyConversation> = listOf(original),
    ) = success(snapshotCollaborationSpaceReadScope(owner, groups, originals, f.at))

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    private fun event(
        room: String,
        sender: String,
        suffix: String,
        type: String = "m.room.encrypted",
        content: Map<String, Any> =
            mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque-$suffix"),
    ): Map<String, Any> =
        mapOf(
            "room_id" to room,
            "sender" to sender,
            "type" to type,
            "content" to content,
            "event_id" to "\$$suffix",
            "origin_server_ts" to 1L,
        ) + (if (type == "m.room.create") mapOf("state_key" to "") else emptyMap())

    private fun creation(room: String, sender: String) =
        event(
            room,
            sender,
            "create-${room.substringBefore(':')}",
            "m.room.create",
            mapOf("room_version" to "12", "m.federate" to false),
        )

    private fun room(room: String, peer: String) =
        mapOf(
            "state" to mapOf("events" to listOf(creation(room, peer))),
            "timeline" to
                mapOf(
                    "events" to listOf(event(room, peer, "message-${room.substringBefore(':')}")),
                    "prev_batch" to "t10",
                    "limited" to true,
                ),
        )

    private fun encrypted(sender: String) =
        mapOf(
            "sender" to sender,
            "type" to "m.room.encrypted",
            "content" to
                mapOf(
                    "algorithm" to "m.olm.v1.curve25519-aes-sha2",
                    "ciphertext" to
                        mapOf("fixture-key" to mapOf("type" to 0, "body" to "opaque-original-key")),
                ),
        )

    private fun response() =
        node(
            mapOf(
                "next_batch" to "s20",
                "rooms" to
                    mapOf(
                        "join" to
                            mapOf(
                                f.roomId to room(f.roomId, oldPeer),
                                group.binding.roomId to room(group.binding.roomId, newPeer),
                                "!foreign" to mapOf("private" to "hidden"),
                            )
                    ),
                "to_device" to
                    mapOf(
                        "events" to
                            listOf(
                                encrypted(oldPeer),
                                encrypted(newPeer),
                                encrypted("@foreign:example.test"),
                            )
                    ),
                "device_lists" to
                    mapOf("changed" to listOf(oldPeer, newPeer, "@foreign:example.test")),
            )
        )

    private class Connection(var response: JsonNode) : MatrixSyncConnection {
        var closes = 0
        var registrations = 0
        var since: String? = null
        var filter: String? = null
        var failure: Exception? = null

        override fun registerFilter(filter: Map<String, Any>): String {
            registrations++
            return "42"
        }

        override fun sync(
            since: String?,
            filterId: String,
            timeoutMillis: Int,
            fullState: Boolean,
        ): JsonNode {
            this.since = since
            filter = filterId
            failure?.let { throw it }
            return response
        }

        override fun roomState(roomId: String): JsonNode =
            error("Creation already supplied by fixture")

        override fun close() {
            closes++
        }
    }

    private var opens = 0

    private fun repository(connection: Connection) =
        InternalCollaborationSpaceMatrixSyncRepository(
            object : MatrixSyncDataSource {
                override fun open(
                    userId: String,
                    deviceId: String,
                    bearer: String,
                    deadline: Instant,
                ): MatrixSyncConnection {
                    opens++
                    assertEquals(owner.matrixUserId, userId)
                    assertEquals(owner.matrixDeviceId, deviceId)
                    assertEquals("fixture-provider-token", bearer)
                    assertEquals(this@MatrixSpaceSyncProjectionTest.deadline, deadline)
                    return connection
                }
            },
            crypto,
        )

    private fun call(
        connection: Connection,
        scope: CollaborationSpaceReadScope = scope(),
        position: CollaborationSyncPosition? = null,
    ) =
        repository(connection)
            .sync(owner, scope, position, 0, false, "fixture-provider-token", deadline)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(
            Result.Success::class.java,
            result,
            (result as? Result.Failed)?.failure?.code,
        )
        return (result as Result.Success).value
    }

    private fun tree(batch: CollaborationMatrixSyncBatch): JsonNode = node(batch.payload)

    @Test
    fun onlyExactGroupSelfControlSurvivesAlongsideOriginalEncryptedKeys() {
        val control =
            CollaborationSpaceReadinessControl(
                UUID.randomUUID(),
                owner.spaceId,
                owner.accountId,
                UUID.randomUUID(),
                owner.deviceId,
                f.at.toEpochMilli(),
                Long.MAX_VALUE,
            )
        val content = collaborationSpaceReadinessControlContent(control)
        fun hint(value: Map<String, Any> = content, sender: String = owner.matrixUserId) =
            mapOf(
                "sender" to sender,
                "type" to COLLABORATION_SPACE_READINESS_CONTROL_TYPE,
                "content" to value,
            )
        val valid = hint()
        val bad =
            listOf(
                hint(sender = oldPeer),
                hint(sender = newPeer),
                hint(content + ("space_id" to UUID.randomUUID().toString())),
                hint(content + ("account_id" to UUID.randomUUID().toString())),
                hint(content + ("device_id" to f.originalDevice.toString())),
                hint(content + ("native_session_id" to "")),
                hint(content + ("published_at" to f.at.toEpochMilli())),
                hint(content + ("publication_revision" to "01")),
                hint(content + ("schema_version" to "2")),
                hint(content + ("body" to "must not escape")),
                hint(content - "expires_at"),
                hint(content + ("event_id" to "x".repeat(4096))),
                hint((content - "space_id") + ("company_id" to owner.spaceId.toString())),
            )
        val retained = encrypted(oldPeer)
        val connection =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s-group-control",
                        "to_device" to mapOf("events" to bad + valid + retained),
                    )
                )
            )
        val batch = success(call(connection))
        assertEquals(node(listOf(valid, retained)), tree(batch).path("to_device").path("events"))
        assertEquals("s-group-control", batch.providerSince)
        assertEquals(1, connection.closes)
    }

    @Test
    fun originalCompanyReadinessDoesNotCrossIntoTheAdoptedSpaceSync() {
        val control =
            CollaborationReadinessControl(
                UUID.randomUUID(),
                f.company,
                owner.accountId,
                UUID.randomUUID(),
                f.originalDevice,
                f.at.toEpochMilli(),
                1L,
            )
        val hint =
            mapOf(
                "sender" to owner.matrixUserId,
                "type" to COLLABORATION_READINESS_CONTROL_TYPE,
                "content" to collaborationReadinessControlContent(control),
            )
        val retained = encrypted(oldPeer)
        val connection =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s-company-control",
                        "to_device" to mapOf("events" to listOf(hint, retained)),
                    )
                )
            )
        val batch = success(call(connection))
        assertEquals(node(listOf(retained)), tree(batch).path("to_device").path("events"))
        assertEquals("s-company-control", batch.providerSince)
        assertEquals(1, connection.closes)
    }

    @Test
    fun originalAndCurrentPeerAliasesKeepQueuedKeysDirectMetadataAndTheirOwnHistoryFraming() {
        val connection = Connection(response())
        val batch = success(call(connection))
        val output = tree(batch)
        assertEquals(
            setOf(f.roomId, group.binding.roomId),
            output.path("rooms").path("join").propertyNames().toSet(),
        )
        assertEquals(
            setOf(oldPeer, newPeer),
            output
                .path("to_device")
                .path("events")
                .toList()
                .map { it.path("sender").asString() }
                .toSet(),
        )
        assertEquals(
            setOf(oldPeer, newPeer),
            output.path("device_lists").path("changed").toList().map { it.asString() }.toSet(),
        )
        assertFalse(output.toString().contains("foreign"))
        val direct = output.path("account_data").path("events").single().path("content")
        assertEquals(listOf(f.roomId), direct.path(oldPeer).toList().map { it.asString() })
        assertEquals(
            listOf(group.binding.roomId),
            direct.path(newPeer).toList().map { it.asString() },
        )
        val oldCursor =
            output
                .path("rooms")
                .path("join")
                .path(f.roomId)
                .path("timeline")
                .path("prev_batch")
                .asString()
        val currentCursor =
            output
                .path("rooms")
                .path("join")
                .path(group.binding.roomId)
                .path("timeline")
                .path("prev_batch")
                .asString()
        assertEquals("t10", openMatrixSpaceRoomCursor(crypto, owner, original, oldCursor))
        assertEquals("t10", openMatrixSpaceRoomCursor(crypto, owner, f.room, oldCursor))
        assertEquals("t10", openMatrixSpaceRoomCursor(crypto, owner, group, currentCursor))
        assertEquals("s20", batch.providerSince)
        assertEquals("42", batch.providerFilterId)
        assertFalse(batch.payload.containsKey("next_batch"))
        assertEquals(1, connection.closes)
    }

    @Test
    fun adoptedRoomAppearsOnceAndInvalidOrPartialScopeNeverOpensProviderConnection() {
        val complete = scope(groups = listOf(f.room))
        val connection = Connection(response())
        val output = tree(success(call(connection, complete)))
        assertEquals(setOf(f.roomId), output.path("rooms").path("join").propertyNames().toSet())
        assertEquals(
            listOf(oldPeer),
            output.path("to_device").path("events").toList().map { it.path("sender").asString() },
        )
        assertEquals(1, opens)
        val foreignOriginal = original.copy(spaceId = UUID.randomUUID())
        for (invalid in
            listOf(
                complete.copy(digest = "a".repeat(64)),
                complete.copy(rooms = complete.rooms + complete.rooms),
                complete.copy(rooms = listOf(CollaborationSpaceReadRoom.Published(f.room))),
                complete.copy(rooms = listOf(CollaborationSpaceReadRoom.Original(foreignOriginal))),
            )) assertInstanceOf(Result.Failed::class.java, call(Connection(response()), invalid))
        assertEquals(1, opens)
    }

    @Test
    fun stableFilterAndConnectionOwnershipSurviveProviderFailureCancellationAndMalformedContent() {
        val state = scope()
        val position = CollaborationSyncPosition(state.digest, "s19", "41")
        val connection = Connection(response())
        success(call(connection, state, position))
        assertEquals(0, connection.registrations)
        assertEquals("s19", connection.since)
        assertEquals("41", connection.filter)
        assertEquals(1, connection.closes)
        val failed = Connection(response()).also { it.failure = MatrixStatusException(503) }
        assertEquals(
            "collaboration_provider_unavailable",
            (call(failed) as Result.Failed).failure.code,
        )
        assertEquals(1, failed.closes)
        val cancelled =
            Connection(response()).also {
                it.failure = CancellationException("fictional sync cancellation")
            }
        assertThrows(CancellationException::class.java) { call(cancelled) }
        assertEquals(1, cancelled.closes)
        val malformed =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s20",
                        "rooms" to
                            mapOf(
                                "join" to
                                    mapOf(
                                        f.roomId to
                                            mapOf(
                                                "timeline" to
                                                    mapOf(
                                                        "events" to
                                                            listOf(
                                                                event(
                                                                    f.roomId,
                                                                    "@foreign:example.test",
                                                                    "wrong",
                                                                )
                                                            )
                                                    )
                                            )
                                    )
                            ),
                    )
                )
            )
        assertEquals(
            "collaboration_provider_invalid_response",
            (call(malformed) as Result.Failed).failure.code,
        )
        assertEquals(1, malformed.closes)
    }
}
