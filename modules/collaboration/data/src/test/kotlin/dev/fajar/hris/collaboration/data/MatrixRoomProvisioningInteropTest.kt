package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.BoundedMatrixBodySubscriber
import dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Provider control semantics only: opaque fixture messages do not prove SDK E2EE. */
class MatrixRoomProvisioningInteropTest {
    @Test
    fun canonicalAliasAndImmutableMarkerRecoverCreationBeforePeerFirstLogin() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val owner = f.login()
            val peerAccount = UUID.randomUUID()
            val peerId =
                "@${collaborationMatrixLocalpart(f.request.companyId, peerAccount)}:${f.config.serverName}"
            val conversationId = UUID.randomUUID()
            val localAlias =
                "hris_${f.request.companyId.toString().replace("-", "")}_${conversationId.toString().replace("-", "")}"
            val alias = "#$localAlias:${f.config.serverName}"
            val marker =
                mapOf(
                    "company_id" to f.request.companyId.toString(),
                    "conversation_id" to conversationId.toString(),
                    "kind" to "DIRECT",
                )
            val request =
                mapOf(
                    "room_version" to "12",
                    "visibility" to "private",
                    "preset" to "private_chat",
                    "room_alias_name" to localAlias,
                    "is_direct" to true,
                    "creation_content" to
                        mapOf("m.federate" to false, "org.hris.conversation" to marker),
                    "initial_state" to
                        listOf(
                            mapOf(
                                "type" to "m.room.encryption",
                                "state_key" to "",
                                "content" to mapOf("algorithm" to "m.megolm.v1.aes-sha2"),
                            ),
                            mapOf(
                                "type" to "m.room.history_visibility",
                                "state_key" to "",
                                "content" to mapOf("history_visibility" to "joined"),
                            ),
                            mapOf(
                                "type" to "m.room.guest_access",
                                "state_key" to "",
                                "content" to mapOf("guest_access" to "forbidden"),
                            ),
                        ),
                    "power_level_content_override" to
                        mapOf(
                            "users_default" to 0,
                            "events_default" to 0,
                            "state_default" to 100,
                            "invite" to 100,
                            "kick" to 100,
                            "ban" to 100,
                            "redact" to 100,
                        ),
                    "invite" to listOf(peerId),
                )
            val create = exchange(f, "/_matrix/client/v3/createRoom", owner.accessToken, request)
            assertEquals(200, create.statusCode(), "Create before the peer's first provider login")
            val roomId = f.json.readTree(create.body())["room_id"].asString()
            val duplicate = exchange(f, "/_matrix/client/v3/createRoom", owner.accessToken, request)
            assertEquals(400, duplicate.statusCode())
            assertEquals("M_ROOM_IN_USE", f.json.readTree(duplicate.body())["errcode"].asString())
            val resolved =
                exchange(
                    f,
                    "/_matrix/client/v3/directory/room/${segment(alias)}",
                    owner.accessToken,
                )
            assertEquals(200, resolved.statusCode())
            assertEquals(roomId, f.json.readTree(resolved.body())["room_id"].asString())
            val state =
                exchange(f, "/_matrix/client/v3/rooms/${segment(roomId)}/state", owner.accessToken)
            assertEquals(200, state.statusCode())
            val events = f.json.readTree(state.body()).iterator().asSequence().toList()
            fun content(type: String) =
                events
                    .single { it["type"].asString() == type && it["state_key"].asString() == "" }[
                        "content"]
            assertEquals(
                f.json.valueToTree<tools.jackson.databind.JsonNode>(marker),
                content("m.room.create")["org.hris.conversation"],
            )
            assertFalse(content("m.room.create")["m.federate"].asBoolean())
            assertEquals("12", content("m.room.create")["room_version"].asString())
            assertEquals(
                "m.megolm.v1.aes-sha2",
                content("m.room.encryption")["algorithm"].asString(),
            )
            assertEquals(
                "joined",
                content("m.room.history_visibility")["history_visibility"].asString(),
            )
            assertEquals("invite", content("m.room.join_rules")["join_rule"].asString())
            assertEquals("forbidden", content("m.room.guest_access")["guest_access"].asString())
            val membership =
                events.single {
                    it["type"].asString() == "m.room.member" && it["state_key"].asString() == peerId
                }
            assertEquals("invite", membership["content"]["membership"].asString())
            val now = f.clock.instant()
            val logged =
                f.repository.login(
                    f.request.copy(
                        accountId = peerAccount,
                        deviceId = UUID.randomUUID(),
                        issuedAt = now,
                        expiresAt = now.plusSeconds(60),
                    )
                )
            assertTrue(logged is Result.Success)
            val peer = (logged as Result.Success).value
            val joined =
                exchange(
                    f,
                    "/_matrix/client/v3/join/${segment(roomId)}",
                    peer.accessToken,
                    emptyMap<String, Any>(),
                )
            assertEquals(200, joined.statusCode())
            val repeatJoin =
                exchange(
                    f,
                    "/_matrix/client/v3/join/${segment(roomId)}",
                    peer.accessToken,
                    emptyMap<String, Any>(),
                )
            assertEquals(200, repeatJoin.statusCode())
            assertEquals(roomId, f.json.readTree(repeatJoin.body())["room_id"].asString())
            val changedAudience =
                exchange(
                    f,
                    "/_matrix/client/v3/rooms/${segment(roomId)}/invite",
                    peer.accessToken,
                    mapOf("user_id" to "@unknown:${f.config.serverName}"),
                )
            assertEquals(403, changedAudience.statusCode())
            val changedEncryption =
                exchange(
                    f,
                    "/_matrix/client/v3/rooms/${segment(roomId)}/state/m.room.encryption",
                    peer.accessToken,
                    mapOf("algorithm" to "unsupported"),
                    "PUT",
                )
            assertEquals(403, changedEncryption.statusCode())
            f.restart()
            val afterRestart =
                exchange(
                    f,
                    "/_matrix/client/v3/directory/room/${segment(alias)}",
                    owner.accessToken,
                )
            assertEquals(200, afterRestart.statusCode())
            assertEquals(roomId, f.json.readTree(afterRestart.body())["room_id"].asString())
        }
    }

    @Test
    fun failedInitialStateLeavesAnAliasThatMustBeVerifiedAndRepairedBeforePublication() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val owner = f.login()
            val aliasLocal = "partial_${UUID.randomUUID().toString().replace("-", "")}"
            val alias = "#$aliasLocal:${f.config.serverName}"
            val marker = UUID.randomUUID().toString()
            val broken =
                mapOf(
                    "room_version" to "12",
                    "visibility" to "private",
                    "preset" to "private_chat",
                    "room_alias_name" to aliasLocal,
                    "creation_content" to
                        mapOf("m.federate" to false, "org.hris.conversation_id" to marker),
                    "initial_state" to
                        listOf(
                            mapOf(
                                "type" to "m.room.power_levels",
                                "state_key" to "",
                                "content" to mapOf("users_default" to "invalid-number"),
                            )
                        ),
                )
            val failed = exchange(f, "/_matrix/client/v3/createRoom", owner.accessToken, broken)
            assertEquals(400, failed.statusCode())
            val aliasResponse =
                exchange(
                    f,
                    "/_matrix/client/v3/directory/room/${segment(alias)}",
                    owner.accessToken,
                )
            assertEquals(
                200,
                aliasResponse.statusCode(),
                "Failed createRoom still reserved the alias",
            )
            val roomId = f.json.readTree(aliasResponse.body())["room_id"].asString()
            val path = "/_matrix/client/v3/rooms/${segment(roomId)}/state"
            val initial = exchange(f, path, owner.accessToken)
            assertEquals(200, initial.statusCode())
            val initialEvents = f.json.readTree(initial.body()).iterator().asSequence().toList()
            assertTrue(initialEvents.none { it["type"].asString() == "m.room.encryption" })
            assertEquals(
                marker,
                initialEvents
                    .single { it["type"].asString() == "m.room.create" }["content"][
                        "org.hris.conversation_id"]
                    .asString(),
            )
            val retried = exchange(f, "/_matrix/client/v3/createRoom", owner.accessToken, broken)
            assertEquals(400, retried.statusCode())
            assertEquals("M_ROOM_IN_USE", f.json.readTree(retried.body())["errcode"].asString())
            val desired =
                linkedMapOf(
                    "m.room.power_levels" to
                        mapOf(
                            "users" to emptyMap<String, Any>(),
                            "users_default" to 0,
                            "events_default" to 0,
                            "state_default" to 100,
                            "invite" to 100,
                            "kick" to 100,
                            "ban" to 100,
                            "redact" to 100,
                        ),
                    "m.room.encryption" to mapOf("algorithm" to "m.megolm.v1.aes-sha2"),
                    "m.room.join_rules" to mapOf("join_rule" to "invite"),
                    "m.room.history_visibility" to mapOf("history_visibility" to "joined"),
                    "m.room.guest_access" to mapOf("guest_access" to "forbidden"),
                )
            for ((type, content) in desired) assertEquals(
                200,
                exchange(f, "$path/$type", owner.accessToken, content, "PUT").statusCode(),
                type,
            )
            val repaired = exchange(f, path, owner.accessToken)
            assertEquals(200, repaired.statusCode())
            val events = f.json.readTree(repaired.body()).iterator().asSequence().toList()
            for ((type, content) in desired) assertEquals(
                f.json.valueToTree<tools.jackson.databind.JsonNode>(content),
                events
                    .single { it["type"].asString() == type && it["state_key"].asString() == "" }[
                        "content"],
            )
            assertEquals(
                marker,
                events
                    .single { it["type"].asString() == "m.room.create" }["content"][
                        "org.hris.conversation_id"]
                    .asString(),
            )
        }
    }

    @Test
    fun productionAdapterCreatesInspectsAndResolvesExactlyTheExpectedDirectRoom() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val owner = f.login()
            val client = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val rooms = InternalCollaborationMatrixRoomRepository(client, f.config.serverName)
            val request =
                CollaborationMatrixDirectRoom(
                    f.request.companyId,
                    UUID.randomUUID(),
                    f.request.accountId,
                    UUID.randomUUID(),
                )
            val deadline = f.clock.instant().plusSeconds(45)
            assertNull(success(rooms.resolve(request, owner.accessToken, deadline)))
            val created =
                success(rooms.create(request, owner.accessToken, deadline))
                    as CollaborationMatrixRoomCreation.Created
            assertEquals(
                created.roomId,
                success(rooms.resolve(request, owner.accessToken, deadline)),
            )
            assertEquals(
                CollaborationMatrixRoomCreation.AliasInUse,
                success(rooms.create(request, owner.accessToken, deadline)),
            )
            val observed = success(rooms.observe(created.roomId, owner.accessToken, deadline))
            assertEquals(
                Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
                assessCollaborationDirectRoom(request, observed, f.config.serverName),
            )
        }
    }

    @Test
    fun productionAdapterRepairsOnlyMissingStateFromAnOwnedPartialCreation() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val owner = f.login()
            val client = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val rooms = InternalCollaborationMatrixRoomRepository(client, f.config.serverName)
            val request =
                CollaborationMatrixDirectRoom(
                    f.request.companyId,
                    UUID.randomUUID(),
                    f.request.accountId,
                    UUID.randomUUID(),
                )
            val deadline = f.clock.instant().plusSeconds(45)
            val broken =
                mapOf(
                    "room_version" to "12",
                    "visibility" to "private",
                    "preset" to "private_chat",
                    "room_alias_name" to collaborationMatrixRoomAliasLocalpart(request),
                    "creation_content" to
                        mapOf(
                            "m.federate" to false,
                            "org.hris.conversation" to
                                mapOf(
                                    "company_id" to request.companyId.toString(),
                                    "conversation_id" to request.conversationId.toString(),
                                    "kind" to "DIRECT",
                                ),
                        ),
                    "initial_state" to
                        listOf(
                            mapOf(
                                "type" to "m.room.power_levels",
                                "state_key" to "",
                                "content" to mapOf("users_default" to "invalid-number"),
                            )
                        ),
                )
            assertEquals(
                400,
                exchange(f, "/_matrix/client/v3/createRoom", owner.accessToken, broken).statusCode(),
            )
            val roomId =
                requireNotNull(success(rooms.resolve(request, owner.accessToken, deadline)))
            val partial = success(rooms.observe(roomId, owner.accessToken, deadline))
            val plan = success(assessCollaborationDirectRoom(request, partial, f.config.serverName))
            assertEquals(CollaborationMatrixRoomSetting.entries.toSet(), plan.missingSettings)
            assertTrue(plan.missingInvite)
            for (setting in CollaborationMatrixRoomSetting.entries) success(
                rooms.putInitialSetting(roomId, setting, owner.accessToken, deadline)
            )
            success(rooms.invitePeer(roomId, request, owner.accessToken, deadline))
            val complete = success(rooms.observe(roomId, owner.accessToken, deadline))
            assertEquals(
                Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
                assessCollaborationDirectRoom(request, complete, f.config.serverName),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationDirectRoom(
                    request.copy(conversationId = UUID.randomUUID()),
                    complete,
                    f.config.serverName,
                ),
            )
        }
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun segment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun exchange(
        f: MatrixProviderFixture,
        path: String,
        token: String,
        body: Any? = null,
        method: String = if (body == null) "GET" else "POST",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(f.config.origin.resolve(path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .method(
                    method,
                    if (body == null) HttpRequest.BodyPublishers.noBody()
                    else HttpRequest.BodyPublishers.ofByteArray(f.json.writeValueAsBytes(body)),
                )
        val future =
            f.client.sendAsync(
                request.build(),
                HttpResponse.BodyHandler { BoundedMatrixBodySubscriber() },
            )
        return try {
            future.get(5, TimeUnit.SECONDS)
        } finally {
            if (!future.isDone) future.cancel(true)
        }
    }
}
