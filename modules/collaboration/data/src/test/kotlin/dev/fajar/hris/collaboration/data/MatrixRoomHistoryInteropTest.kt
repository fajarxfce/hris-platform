package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.snapshotCollaborationRoomScope
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixRoomHistoryInteropTest {
    @Test
    fun actualSyncPositionsPaginateEncryptedHistoryAndContextInBothDirectionsToTermination() {
        MatrixProviderFixture("10m", messageBurst = 100).use { f ->
            f.start()
            val creator = f.login()
            val account = UUID.randomUUID()
            val device = UUID.randomUUID()
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            accountId = account,
                            deviceId = device,
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            val source = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val conversation = UUID.randomUUID()
            val id =
                ((InternalCollaborationMatrixRoomRepository(source, f.config.serverName)
                            .create(
                                CollaborationMatrixDirectRoom(
                                    f.request.companyId,
                                    conversation,
                                    f.request.accountId,
                                    account,
                                ),
                                creator.accessToken,
                                f.clock.instant().plusSeconds(20),
                            ) as Result.Success)
                        .value as CollaborationMatrixRoomCreation.Created)
                    .roomId
            val room =
                CollaborationOwnedRoom(
                    f.request.companyId,
                    conversation,
                    f.request.accountId,
                    account,
                    f.request.accountId,
                    f.config.serverName,
                    id,
                )
            val owner =
                CollaborationSyncOwner(
                    f.request.companyId,
                    f.request.accountId,
                    f.request.deviceId,
                    f.config.serverName,
                )

            val crypto = collaborationCursorFixture()
            val history = InternalCollaborationMatrixRoomHistoryRepository(source, crypto)
            fun deadline() = f.clock.instant().plusSeconds(20)
            fun success(result: Result<CollaborationRoomHistory>): Map<String, Any> {
                assertInstanceOf(Result.Success::class.java, result)
                return (result as Result.Success).value.payload
            }
            val json = tools.jackson.databind.json.JsonMapper.builder().build()
            fun encode(value: String) =
                java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
            fun send(index: Int): String {
                val response =
                    f.client.send(
                        java.net.http.HttpRequest.newBuilder(
                                f.config.origin.resolve(
                                    "/_matrix/client/v3/rooms/${encode(id)}/send/m.room.encrypted/fixture-$index"
                                )
                            )
                            .timeout(java.time.Duration.ofSeconds(5))
                            .header("Authorization", "Bearer ${creator.accessToken}")
                            .header("Content-Type", "application/json")
                            .PUT(
                                java.net.http.HttpRequest.BodyPublishers.ofString(
                                    json.writeValueAsString(
                                        mapOf(
                                            "algorithm" to "m.megolm.v1.aes-sha2",
                                            "ciphertext" to "opaque-fixture-$index",
                                            "session_id" to "fixture",
                                            "device_id" to creator.deviceId,
                                            "sender_key" to "fixture",
                                        )
                                    )
                                )
                            )
                            .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(200, response.statusCode())
                return json.readTree(response.body()).path("event_id").asString()
            }
            val sent = (1..25).map(::send)
            val scope =
                (snapshotCollaborationRoomScope(
                        owner.companyId,
                        owner.accountId,
                        owner.serverName,
                        listOf(room),
                    )
                        as Result.Success)
                    .value
            val syncBatch =
                HttpMatrixSyncDataSource(f.client, f.config, f.clock).use { sync ->
                    val result =
                        InternalCollaborationMatrixSyncRepository(sync, crypto)
                            .sync(owner, scope, null, 0, false, creator.accessToken, deadline())
                    assertInstanceOf(Result.Success::class.java, result)
                    (result as Result.Success).value
                }
            val timeline =
                (((syncBatch.payload["rooms"] as Map<*, *>)["join"] as Map<*, *>)[id] as Map<*, *>)[
                    "timeline"]
                    as Map<*, *>
            val previous = timeline["prev_batch"] as String
            val older =
                success(
                    history.messages(
                        owner,
                        room,
                        CollaborationRoomPageRequest(previous, null, "b", 10),
                        creator.accessToken,
                        deadline(),
                    )
                )
            assertTrue((older["chunk"] as List<*>).isNotEmpty())
            assertTrue(
                (older["chunk"] as List<*>).any { (it as Map<*, *>)["event_id"] == sent.first() }
            )
            val seen = mutableSetOf<String>()
            var cursor: String? = null
            var pages = 0
            var initialHead: String? = null
            do {
                check(pages++ < 10) { "History did not terminate within its fixture bound" }
                val page =
                    success(
                        history.messages(
                            owner,
                            room,
                            CollaborationRoomPageRequest(cursor, null, "b", 10),
                            creator.accessToken,
                            deadline(),
                        )
                    )
                if (initialHead == null) initialHead = page["start"] as String
                for (event in page["chunk"] as List<*>) assertTrue(
                    seen.add((event as Map<*, *>)["event_id"] as String)
                )
                cursor = page["end"] as String?
            } while (cursor != null)
            assertTrue(seen.containsAll(sent))
            assertTrue(pages >= 3)
            val target = sent[12]
            assertEquals(
                target,
                success(history.event(owner, room, target, creator.accessToken, deadline()))[
                    "event_id"],
            )
            val context =
                success(history.context(owner, room, target, 4, creator.accessToken, deadline()))
            assertEquals(target, (context["event"] as Map<*, *>)["event_id"])
            assertEquals(
                4,
                (context["events_before"] as List<*>).size +
                    (context["events_after"] as List<*>).size,
            )
            success(
                history.messages(
                    owner,
                    room,
                    CollaborationRoomPageRequest(context["start"] as String, null, "b", 2),
                    creator.accessToken,
                    deadline(),
                )
            )
            success(
                history.messages(
                    owner,
                    room,
                    CollaborationRoomPageRequest(context["end"] as String, null, "f", 2),
                    creator.accessToken,
                    deadline(),
                )
            )
            val missing =
                history.event(owner, room, "\$missing", creator.accessToken, deadline())
                    as Result.Failed
            assertEquals("collaboration_room_event_not_found", missing.failure.code)

            // The provider's explicit `to` boundary isolates completed waves without timestamp
            // reconstruction or stopping at an arbitrary overlapping event. New sends during a
            // partial scan belong to the next wave, even after the adapter is recreated.
            val delayed = (26..72).map(::send)
            val first =
                success(
                    history.messages(
                        owner,
                        room,
                        CollaborationRoomPageRequest(null, initialHead, "b", 7),
                        creator.accessToken,
                        deadline(),
                    )
                )
            val waveHead = first["start"] as String
            val later = (73..77).map(::send)
            fun boundedWave(firstPage: Map<String, Any>, stop: String): List<String> {
                val acquired = mutableListOf<String>()
                var page = firstPage
                var steps = 0
                do {
                    check(steps++ < 20) { "Bounded provider wave did not terminate" }
                    acquired +=
                        (page["chunk"] as List<*>).map { (it as Map<*, *>)["event_id"] as String }
                    val next = page["end"] as String? ?: break
                    page =
                        success(
                            InternalCollaborationMatrixRoomHistoryRepository(source, crypto)
                                .messages(
                                    owner,
                                    room,
                                    CollaborationRoomPageRequest(next, stop, "b", 7),
                                    creator.accessToken,
                                    deadline(),
                                )
                        )
                } while (true)
                assertEquals(acquired.size, acquired.distinct().size)
                return acquired
            }
            assertEquals(delayed.reversed(), boundedWave(first, requireNotNull(initialHead)))
            val second =
                success(
                    history.messages(
                        owner,
                        room,
                        CollaborationRoomPageRequest(null, waveHead, "b", 7),
                        creator.accessToken,
                        deadline(),
                    )
                )
            assertEquals(later.reversed(), boundedWave(second, waveHead))
            val caughtUp = second["start"] as String
            val empty =
                success(
                    history.messages(
                        owner,
                        room,
                        CollaborationRoomPageRequest(null, caughtUp, "b", 7),
                        creator.accessToken,
                        deadline(),
                    )
                )
            assertTrue(boundedWave(empty, caughtUp).isEmpty())
        }
    }
}
