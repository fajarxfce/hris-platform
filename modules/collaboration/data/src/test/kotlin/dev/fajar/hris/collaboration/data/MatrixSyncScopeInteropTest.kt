package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.BoundedMatrixBodySubscriber
import dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

/** Actual provider cursor/filter behavior, not HRIS authorization or SDK acceptance. */
class MatrixSyncScopeInteropTest {
    @Test
    fun olderJoinedAndInvitedRoomsNeedStateReacquisitionWhenTheirFilterScopeExpands() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val owner = f.login()
            val peerAccount = UUID.randomUUID()
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            accountId = peerAccount,
                            deviceId = UUID.randomUUID(),
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            val rooms =
                InternalCollaborationMatrixRoomRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    f.config.serverName,
                )
            // Two provider-only fixture rooms model a filter change; this does not create
            // two HRIS canonical direct intents for one pair.
            fun create() =
                ((rooms.create(
                            CollaborationMatrixDirectRoom(
                                f.request.companyId,
                                UUID.randomUUID(),
                                f.request.accountId,
                                peerAccount,
                            ),
                            owner.accessToken,
                            f.clock.instant().plusSeconds(20),
                        ) as Result.Success)
                        .value as CollaborationMatrixRoomCreation.Created)
                    .roomId
            val first = create()
            val second = create()
            fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
            fun sync(
                token: String,
                selected: List<String>,
                since: String? = null,
                full: Boolean = false,
            ): JsonNode {
                val filter =
                    f.json.writeValueAsString(
                        mapOf(
                            "room" to
                                mapOf(
                                    "rooms" to selected,
                                    "timeline" to mapOf("limit" to 20),
                                    "state" to mapOf("lazy_load_members" to false),
                                ),
                            "presence" to mapOf("types" to emptyList<String>()),
                        )
                    )
                val suffix = since?.let { "&since=" + encode(it) } ?: ""
                val response =
                    f.client.send(
                        HttpRequest.newBuilder(
                                f.config.origin.resolve(
                                    "/_matrix/client/v3/sync?timeout=0&filter=${encode(filter)}&full_state=$full$suffix"
                                )
                            )
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer $token")
                            .GET()
                            .build(),
                        HttpResponse.BodyHandler { BoundedMatrixBodySubscriber(1_048_576) },
                    )
                assertEquals(200, response.statusCode())
                return f.json.readTree(response.body())
            }
            for ((token, section) in
                listOf(owner.accessToken to "join", peer.accessToken to "invite")) {
                val initial = sync(token, listOf(first))
                assertEquals(
                    if (section == "join") setOf(first) else setOf(first, second),
                    initial.path("rooms").path(section).propertyNames().toSet(),
                )
                val since = initial.path("next_batch").asString()
                val incremental = sync(token, listOf(first, second), since)
                assertFalse(incremental.path("rooms").path(section).has(second))
                val full = sync(token, listOf(first, second), since, full = true)
                // Synapse's room filter does not exclude invitation buckets. The gateway
                // must filter all buckets itself before exposing any provider response.
                // Nor does full_state replay old invitations when since is supplied.
                val reacquired =
                    if (section == "join") {
                        assertTrue(full.path("rooms").path(section).has(second))
                        full
                    } else {
                        assertFalse(full.path("rooms").path(section).has(second))
                        sync(token, listOf(first, second))
                    }
                assertTrue(reacquired.path("rooms").path(section).has(second))
                val room = reacquired.path("rooms").path(section).path(second)
                // Sync state is the state BEFORE the timeline; initial state events may
                // therefore be carried in the timeline instead of repeated in state.events.
                val state =
                    if (section == "join") {
                        val base = room.path("state").path("events")
                        val timeline = room.path("timeline").path("events")
                        assertTrue(base.isArray && timeline.isArray)
                        (base.toList() + timeline.toList()).filter { it.has("state_key") }
                    } else {
                        val invite = room.path("invite_state").path("events")
                        assertTrue(invite.isArray)
                        invite.toList()
                    }
                if (section == "join") {
                    assertTrue(state.any { it.path("type").asString() == "m.room.encryption" })
                    // In this invited-history fixture the creator's m.room.create is
                    // omitted from sync even with full_state. A plain reset
                    // does not recover it either. The explicit current state endpoint does.
                    assertFalse(state.any { it.path("type").asString() == "m.room.create" })
                    val reset =
                        sync(token, listOf(first, second)).path("rooms").path("join").path(second)
                    val resetEvents =
                        reset.path("state").path("events").toList() +
                            reset.path("timeline").path("events").toList()
                    assertFalse(resetEvents.any { it.path("type").asString() == "m.room.create" })
                    val response =
                        f.client.send(
                            HttpRequest.newBuilder(
                                    f.config.origin.resolve(
                                        "/_matrix/client/v3/rooms/${encode(second)}/state"
                                    )
                                )
                                .timeout(Duration.ofSeconds(5))
                                .header("Authorization", "Bearer $token")
                                .GET()
                                .build(),
                            HttpResponse.BodyHandler { BoundedMatrixBodySubscriber(1_048_576) },
                        )
                    assertEquals(200, response.statusCode())
                    val creation =
                        f.json.readTree(response.body()).single {
                            it.path("type").asString() == "m.room.create"
                        }
                    assertEquals("12", creation.path("content").path("room_version").asString())
                    assertEquals(owner.userId, creation.path("sender").asString())
                    assertEquals(second, creation.path("room_id").asString())
                    assertTrue(
                        (state + listOf(creation)).any {
                            it.path("type").asString() == "m.room.create"
                        }
                    )
                } else
                    assertTrue(
                        state.any {
                            it.path("type").asString() == "m.room.member" &&
                                it.path("state_key").asString() == peer.userId &&
                                it.path("content").path("membership").asString() == "invite"
                        }
                    )
            }
        }
    }
}
