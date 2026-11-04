package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class CollaborationSpaceMatrixRoomRepositoryTest {
    @Test
    fun creationUsesExplicitCanonicalUsersAndASeparateImmutableGroupMarker() {
        val f = Fixture()
        val created = success(f.repo.create(f.request, f.token, f.deadline))
        assertEquals(CollaborationMatrixRoomCreation.Created(f.raw.id), created)
        val body = requireNotNull(f.raw.body)
        assertEquals(collaborationSpaceMatrixRoomAliasLocalpart(f.request), body["room_alias_name"])
        assertEquals(listOf(f.request.peerUserId), body["invite"])
        val content = body["creation_content"] as Map<*, *>
        assertEquals(false, content["m.federate"])
        assertFalse(content.containsKey("org.hris.conversation"))
        assertEquals(
            mapOf(
                "space_id" to f.request.spaceId.toString(),
                "conversation_id" to f.request.conversationId.toString(),
                "kind" to "DIRECT",
            ),
            content["org.hris.space_conversation"],
        )
        f.raw.events = f.events(body)
        val observed = success(f.repo.observe(f.raw.id, f.token, f.deadline))
        assertEquals(
            Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
            assessCollaborationSpaceDirectRoom(f.request, observed, f.server),
        )
        assertEquals(f.raw.id, success(f.repo.resolve(f.request, f.token, f.deadline)))
        assertEquals(
            "#${collaborationSpaceMatrixRoomAliasLocalpart(f.request)}:${f.server}",
            f.raw.alias,
        )
        success(f.repo.invitePeer(f.raw.id, f.request, f.token, f.deadline))
        assertEquals(f.request.peerUserId, f.raw.invited)
    }

    @Test
    fun duplicateOversizedOrMalformedStateNeverBecomesARepairableObservation() {
        val f = Fixture()
        success(f.repo.create(f.request, f.token, f.deadline))
        val original = f.events(requireNotNull(f.raw.body))
        for (mode in listOf("duplicate", "oversize", "marker", "encryption", "power", "member")) {
            f.raw.events =
                when (mode) {
                    "duplicate" -> original + original.first()
                    "oversize" ->
                        (0..256).map {
                            MatrixRoomStateEventData(
                                "org.fixture.$it",
                                "",
                                f.request.creatorUserId,
                                f.json.createObjectNode(),
                            )
                        }
                    else ->
                        original.map { event ->
                            when {
                                mode == "marker" && event.type == "m.room.create" ->
                                    event.copy(
                                        content =
                                            f.json.valueToTree(
                                                mapOf(
                                                    "room_version" to "12",
                                                    "m.federate" to false,
                                                    "org.hris.space_conversation" to
                                                        mapOf(
                                                            "space_id" to
                                                                f.request.spaceId.toString()
                                                        ),
                                                )
                                            )
                                    )
                                mode == "encryption" && event.type == "m.room.encryption" ->
                                    event.copy(
                                        content = f.json.valueToTree(mapOf("algorithm" to 7))
                                    )
                                mode == "power" && event.type == "m.room.power_levels" ->
                                    event.copy(content = f.json.createObjectNode())
                                mode == "member" && event.type == "m.room.member" ->
                                    event.copy(
                                        content =
                                            f.json.valueToTree(
                                                mapOf("membership" to listOf("join"))
                                            )
                                    )
                                else -> event
                            }
                        }
                }
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.observe(f.raw.id, f.token, f.deadline),
                mode,
            )
        }
        f.raw.events = original
        assertInstanceOf(Result.Success::class.java, f.repo.observe(f.raw.id, f.token, f.deadline))
    }

    @Test
    fun rawRoomReadbackAndAcquisitionFailuresShareTheSafeBoundary() {
        val f = Fixture()
        for (bad in listOf("", "foreign", "!has space", "!" + "a".repeat(255), "!line\n")) {
            f.raw.id = bad
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.create(f.request, f.token, f.deadline),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.resolve(f.request, f.token, f.deadline),
            )
        }
        f.raw.id = "!owned:matrix.example.test"
        val operations: List<() -> Result<*>> =
            listOf(
                { f.repo.create(f.request, f.token, f.deadline) },
                { f.repo.resolve(f.request, f.token, f.deadline) },
                { f.repo.observe(f.raw.id, f.token, f.deadline) },
                { f.repo.join(f.raw.id, f.token, f.deadline) },
                { f.repo.invitePeer(f.raw.id, f.request, f.token, f.deadline) },
                {
                    f.repo.putInitialSetting(
                        f.raw.id,
                        CollaborationMatrixRoomSetting.ENCRYPTION,
                        f.token,
                        f.deadline,
                    )
                },
            )
        for (mode in listOf("io", "cancel", "interrupt")) {
            f.raw.failure = mode
            operations.forEach {
                when (mode) {
                    "cancel" -> assertThrows(CancellationException::class.java) { it() }
                    "interrupt" -> assertThrows(InterruptedException::class.java) { it() }
                    else -> assertInstanceOf(Result.Failed::class.java, it())
                }
            }
        }
    }

    @Test
    fun invalidCanonicalRequestsAndRoomPathsAreRejectedBeforeRawIo() {
        val f = Fixture()
        for (value in
            listOf(
                f.request.copy(spaceId = UUID(0, 0)),
                f.request.copy(peerAccountId = UUID.randomUUID()),
                f.request.copy(peerUserId = f.request.creatorUserId),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.repo.resolve(value, f.token, f.deadline))
            assertInstanceOf(Result.Failed::class.java, f.repo.create(value, f.token, f.deadline))
            assertInstanceOf(
                Result.Failed::class.java,
                f.repo.invitePeer(f.raw.id, value, f.token, f.deadline),
            )
        }
        assertInstanceOf(Result.Failed::class.java, f.repo.observe("invalid", f.token, f.deadline))
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.putInitialSetting(
                "invalid",
                CollaborationMatrixRoomSetting.ENCRYPTION,
                f.token,
                f.deadline,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            InternalCollaborationSpaceMatrixRoomRepository(f.raw, "other.example.test")
                .create(f.request, f.token, f.deadline),
        )
        assertEquals(0, f.raw.calls)
    }

    @Test
    fun joiningRequiresExactRoomReadbackAndPreservesCancellation() {
        val f = Fixture()
        assertEquals(Result.Success(Unit), f.repo.join(f.raw.id, f.token, f.deadline))
        for (invalid in
            listOf(
                f.json.createObjectNode(),
                f.json.createObjectNode().put("room_id", "!other:matrix.example.test"),
                f.json.createObjectNode().put("room_id", 123),
                f.json.createArrayNode(),
            )) {
            f.raw.joinResponse = invalid
            assertInstanceOf(Result.Failed::class.java, f.repo.join(f.raw.id, f.token, f.deadline))
        }
        val calls = f.raw.calls
        assertInstanceOf(
            Result.Failed::class.java,
            f.repo.join("!invalid room", f.token, f.deadline),
        )
        assertEquals(calls, f.raw.calls)
        f.raw.failure = "cancel"
        assertThrows(CancellationException::class.java) {
            f.repo.join(f.raw.id, f.token, f.deadline)
        }
        f.raw.failure = "interrupt"
        try {
            assertThrows(InterruptedException::class.java) {
                f.repo.join(f.raw.id, f.token, f.deadline)
            }
        } finally {
            Thread.interrupted()
        }
    }

    private class Raw : MatrixRoomDataSource {
        var id = "!owned:matrix.example.test"
        var alias: String? = null
        var invited: String? = null
        var body: Map<String, Any>? = null
        var joinResponse: JsonNode? = null
        var events = emptyList<MatrixRoomStateEventData>()
        var failure: String? = null
        var calls = 0

        private fun acquire() {
            calls++
            when (failure) {
                "io" -> throw IOException("fixture acquisition")
                "cancel" -> throw CancellationException("fixture acquisition")
                "interrupt" -> throw InterruptedException("fixture acquisition")
            }
        }

        override fun resolveAlias(alias: String, token: String, deadline: Instant): String? {
            acquire()
            this.alias = alias
            return id
        }

        override fun createRoom(
            body: Map<String, Any>,
            token: String,
            deadline: Instant,
        ): MatrixRoomCreateData {
            acquire()
            this.body = body
            return MatrixRoomCreateData.Created(id)
        }

        override fun join(roomId: String, token: String, deadline: Instant): JsonNode {
            acquire()
            return joinResponse
                ?: JsonMapper.builder().build().createObjectNode().put("room_id", roomId)
        }

        override fun roomState(
            roomId: String,
            token: String,
            deadline: Instant,
        ): List<MatrixRoomStateEventData> {
            acquire()
            return events
        }

        override fun putRoomState(
            roomId: String,
            type: MatrixRoomStateType,
            content: Map<String, Any>,
            token: String,
            deadline: Instant,
        ) {
            acquire()
        }

        override fun invite(roomId: String, userId: String, token: String, deadline: Instant) {
            acquire()
            invited = userId
        }
    }

    private class Fixture {
        val raw = Raw()
        val server = "matrix.example.test"
        val token = "fixture-only"
        val deadline = Instant.now().plusSeconds(20)
        val repo = InternalCollaborationSpaceMatrixRoomRepository(raw, server)
        val json = JsonMapper.builder().build()
        private val creator = UUID.randomUUID()
        private val peer = UUID.randomUUID()
        private val space = UUID.randomUUID()
        val request =
            CollaborationSpaceMatrixDirectRoom(
                space,
                UUID.randomUUID(),
                creator,
                peer,
                "@hris_${compact(UUID.randomUUID())}_${compact(creator)}:$server",
                "@hrisg_${compact(space)}_${compact(peer)}:$server",
            )

        fun events(body: Map<String, Any>): List<MatrixRoomStateEventData> {
            val root = json.valueToTree<JsonNode>(body)
            val create =
                json.convertValue(root["creation_content"], Map::class.java).entries.associate {
                    it.key.toString() to requireNotNull(it.value)
                } + ("room_version" to "12")
            fun event(type: String, content: JsonNode, key: String = "") =
                MatrixRoomStateEventData(type, key, request.creatorUserId, content)
            return listOf(
                event("m.room.create", json.valueToTree(create)),
                event("m.room.power_levels", root["power_level_content_override"]),
            ) +
                root["initial_state"]
                    .iterator()
                    .asSequence()
                    .map { event(it["type"].asString(), it["content"]) }
                    .toList() +
                listOf(
                    event(
                        "m.room.member",
                        json.valueToTree(mapOf("membership" to "join")),
                        request.creatorUserId,
                    ),
                    event(
                        "m.room.member",
                        json.valueToTree(mapOf("membership" to "invite")),
                        request.peerUserId,
                    ),
                )
        }

        private fun compact(value: UUID) = value.toString().replace("-", "")
    }

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
