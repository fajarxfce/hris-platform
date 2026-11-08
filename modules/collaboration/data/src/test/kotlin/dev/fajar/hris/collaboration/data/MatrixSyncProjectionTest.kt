package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixSyncRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class MatrixSyncProjectionTest {
    private val json = JsonMapper.builder().build()
    private val owner =
        CollaborationSyncOwner(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "proof.invalid",
        )
    private val peer = UUID.randomUUID()
    private val room =
        CollaborationOwnedRoom(
            owner.companyId,
            UUID.randomUUID(),
            owner.accountId,
            peer,
            owner.accountId,
            owner.serverName,
            "!owned",
        )
    private val scope =
        (snapshotCollaborationRoomScope(
                owner.companyId,
                owner.accountId,
                owner.serverName,
                listOf(room),
            )
                as Result.Success)
            .value

    private fun user(id: UUID) =
        "@${collaborationMatrixLocalpart(owner.companyId, id)}:${owner.serverName}"

    private fun node(value: Any): JsonNode = json.readTree(json.writeValueAsBytes(value))

    private fun event(
        type: String,
        content: Map<String, Any>,
        sender: String = user(owner.accountId),
    ) =
        mapOf(
            "type" to type,
            "sender" to sender,
            "content" to content,
            "event_id" to "\$fixture",
            "origin_server_ts" to 1L,
            "state_key" to "",
        )

    @Test
    fun onlyExactOwnReadinessControlSurvivesWithoutPoisoningEncryptedSync() {
        val control =
            CollaborationReadinessControl(
                UUID.randomUUID(),
                owner.companyId,
                owner.accountId,
                UUID.randomUUID(),
                owner.deviceId,
                1_794_171_600_123L,
                1L,
            )
        val content = collaborationReadinessControlContent(control)
        fun hint(value: Map<String, Any> = content, sender: String = user(owner.accountId)) =
            mapOf(
                "sender" to sender,
                "type" to COLLABORATION_READINESS_CONTROL_TYPE,
                "content" to value,
            )
        val valid = hint()
        val encrypted =
            mapOf(
                "sender" to user(peer),
                "type" to "m.room.encrypted",
                "content" to
                    mapOf("algorithm" to "m.olm.v1.curve25519-aes-sha2", "ciphertext" to "opaque"),
            )
        val bad =
            listOf(
                hint(sender = user(peer)),
                hint(content + ("company_id" to UUID.randomUUID().toString())),
                hint(content + ("account_id" to UUID.randomUUID().toString())),
                hint(content + ("device_id" to UUID.randomUUID().toString())),
                hint(content + ("native_session_id" to "")),
                hint(content + ("published_at" to 1_794_171_600_123L)),
                hint(content + ("schema_version" to "1")),
                hint(content + ("body" to "must not escape")),
                hint(content - "expires_at"),
                hint(content + ("event_id" to "x".repeat(4096))),
            )
        val connection =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s-control",
                        "to_device" to mapOf("events" to bad + valid + encrypted),
                    )
                )
            )
        val batch = (call(connection) as Result.Success).value
        assertEquals(
            node(listOf(valid, encrypted)),
            node(batch.payload).path("to_device").path("events"),
        )
        assertEquals("s-control", batch.providerSince)
        assertEquals(1, connection.closed)
    }

    @Test
    fun everyForeignBucketAndMetadataSurfaceIsExcludedAndDirectMappingComesFromHris() {
        val foreign = "@private:other.invalid"
        val invite =
            mapOf(
                "invite_state" to
                    mapOf(
                        "events" to
                            listOf(
                                event(
                                    "m.room.encryption",
                                    mapOf("algorithm" to "m.megolm.v1.aes-sha2"),
                                )
                            )
                    )
            )
        val connection =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s1",
                        "rooms" to
                            mapOf(
                                "invite" to
                                    mapOf(
                                        "!owned" to invite,
                                        "!foreign-invite" to mapOf("private" to foreign),
                                    ),
                                "join" to mapOf("!foreign-join" to mapOf("private" to foreign)),
                                "leave" to mapOf("!foreign-left" to mapOf("private" to foreign)),
                                "knock" to mapOf("!foreign-knock" to mapOf("private" to foreign)),
                            ),
                        "to_device" to
                            mapOf(
                                "events" to
                                    listOf(
                                        mapOf(
                                            "sender" to foreign,
                                            "type" to "m.room.encrypted",
                                            "content" to mapOf("private" to foreign),
                                        ),
                                        mapOf(
                                            "sender" to user(peer),
                                            "type" to "m.secret.send",
                                            "content" to mapOf("private" to foreign),
                                        ),
                                    )
                            ),
                        "device_lists" to
                            mapOf(
                                "changed" to listOf(foreign, user(peer)),
                                "left" to listOf(foreign),
                            ),
                        "presence" to mapOf("private" to foreign),
                        "account_data" to mapOf("private" to foreign),
                    )
                )
            )
        val result = call(connection) as Result.Success
        val output = json.writeValueAsString(result.value.payload)
        assertFalse(output.contains(foreign))
        assertFalse(output.contains("foreign-"))
        assertFalse(output.contains("m.secret.send"))
        assertTrue(output.contains("m.direct"))
        assertTrue(output.contains(user(peer)))
        assertEquals(1, connection.closed)
    }

    @Test
    fun plaintextAlienSenderAndInvalidCreateCannotAdvanceSyncAndAlwaysCloseConnection() {
        for (bad in
            listOf(
                event("m.room.message", mapOf("body" to "unencrypted")),
                event(
                    "m.room.encrypted",
                    mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque"),
                    "@alien:proof.invalid",
                ),
                event("m.room.create", mapOf("room_version" to "12", "m.federate" to true)),
            )) {
            val connection =
                Connection(
                    node(
                        mapOf(
                            "next_batch" to "s1",
                            "rooms" to
                                mapOf(
                                    "join" to
                                        mapOf(
                                            "!owned" to
                                                mapOf("state" to mapOf("events" to listOf(bad)))
                                        )
                                ),
                        )
                    )
                )
            assertEquals(
                "collaboration_provider_invalid_response",
                (call(connection) as Result.Failed).failure.code,
            )
            assertEquals(1, connection.closed)
        }
        val missing =
            Connection(
                node(
                    mapOf(
                        "next_batch" to "s1",
                        "rooms" to mapOf("join" to mapOf("!owned" to emptyMap<String, Any>())),
                    )
                )
            )
        assertInstanceOf(Result.Failed::class.java, call(missing))
        assertEquals(1, missing.stateReads)
        assertEquals(1, missing.closed)
    }

    @Test
    fun missingCreationHydratesOnlyTheImmutableEventBeforeTheOlderTimeline() {
        val encrypted =
            event(
                    "m.room.encrypted",
                    mapOf("algorithm" to "m.megolm.v1.aes-sha2", "ciphertext" to "opaque-fixture"),
                )
                .minus("state_key")
        val connection =
            Connection(
                    node(
                        mapOf(
                            "next_batch" to "s1",
                            "rooms" to
                                mapOf(
                                    "join" to
                                        mapOf(
                                            "!owned" to
                                                mapOf(
                                                    "timeline" to
                                                        mapOf("events" to listOf(encrypted))
                                                )
                                        )
                                ),
                        )
                    )
                )
                .apply {
                    state =
                        node(
                            listOf(
                                event(
                                    "m.room.create",
                                    mapOf("room_version" to "12", "m.federate" to false),
                                ),
                                event("m.room.name", mapOf("name" to "later-mutable-state")),
                            )
                        )
                }
        val result = call(connection) as Result.Success
        val output = node(result.value.payload).path("rooms").path("join").path("!owned")
        assertEquals(
            "m.room.create",
            output.path("state").path("events").single().path("type").asString(),
        )
        assertEquals(
            "m.room.encrypted",
            output.path("timeline").path("events").single().path("type").asString(),
        )
        assertFalse(output.toString().contains("later-mutable-state"))
        assertEquals(1, connection.stateReads)
        assertEquals(1, connection.closed)
    }

    private fun call(connection: Connection) =
        InternalCollaborationMatrixSyncRepository(
                object : MatrixSyncDataSource {
                    override fun open(
                        userId: String,
                        deviceId: String,
                        bearer: String,
                        deadline: Instant,
                    ): MatrixSyncConnection = connection
                },
                collaborationCursorFixture(),
            )
            .sync(owner, scope, null, 0, false, "fixture", Instant.now().plusSeconds(30))

    private inner class Connection(val response: JsonNode) : MatrixSyncConnection {
        var closed = 0
        var stateReads = 0
        var state: JsonNode = json.createArrayNode()

        override fun registerFilter(filter: Map<String, Any>): String {
            assertFalse(json.writeValueAsString(filter).contains("!owned"))
            val roomFilter = filter.getValue("room") as Map<*, *>
            assertEquals(mapOf("types" to listOf("m.receipt")), roomFilter["ephemeral"])
            return "0"
        }

        override fun sync(
            since: String?,
            filterId: String,
            timeoutMillis: Int,
            fullState: Boolean,
        ) = response

        override fun roomState(roomId: String): JsonNode {
            assertEquals("!owned", roomId)
            stateReads++
            return state
        }

        override fun close() {
            closed++
        }
    }
}
