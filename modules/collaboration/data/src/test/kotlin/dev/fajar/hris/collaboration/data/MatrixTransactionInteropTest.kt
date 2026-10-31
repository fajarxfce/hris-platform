package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.BoundedMatrixBodySubscriber
import dev.fajar.hris.core.domain.Result
import java.net.http.*
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixTransactionInteropTest {
    @Test
    fun bearerReplacementKeepsTransactionIdentityButAgedMappingCleanupAndRestartPermitADuplicateEvent() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val first = f.login()
            val room =
                exchange(
                    f,
                    "/_matrix/client/v3/createRoom",
                    first.accessToken,
                    f.json.writeValueAsBytes(
                        mapOf(
                            "preset" to "private_chat",
                            "initial_state" to
                                listOf(
                                    mapOf(
                                        "type" to "m.room.encryption",
                                        "state_key" to "",
                                        "content" to mapOf("algorithm" to "m.megolm.v1.aes-sha2"),
                                    )
                                ),
                        )
                    ),
                )
            assertEquals(200, room.statusCode())
            val roomId = f.json.readTree(room.body())["room_id"].asString()
            val transaction = "owned-" + UUID.randomUUID()
            val path = "/_matrix/client/v3/rooms/$roomId/send/m.room.encrypted/$transaction"
            // Opaque fictional content isolates transaction semantics. It is not an E2EE proof.
            val content =
                f.json.writeValueAsBytes(
                    mapOf(
                        "algorithm" to "m.megolm.v1.aes-sha2",
                        "ciphertext" to "b3duZWQtZHVwbGljYXRlLWZpeHR1cmU",
                        "session_id" to "owned-session",
                        "device_id" to first.deviceId,
                    )
                )
            val sent = exchange(f, path, first.accessToken, content, "PUT")
            assertEquals(200, sent.statusCode())
            val eventId = f.json.readTree(sent.body())["event_id"].asString()
            val second = f.login()
            assertNotEquals(first.accessToken, second.accessToken)
            assertEquals(first.deviceId, second.deviceId)
            val replay = exchange(f, path, second.accessToken, content, "PUT")
            assertEquals(200, replay.statusCode())
            assertEquals(eventId, f.json.readTree(replay.body())["event_id"].asString())

            // Own only this disposable provider database. Age this mapping, execute the pinned
            // cleanup predicate, then restart to remove the in-memory response cache. We do not
            // claim to have waited 24 hours or exercised the five-minute timer's scheduling.
            val cleanup =
                f.server.execInContainer(
                    "python",
                    "-c",
                    """
                    import sqlite3,sys,time
                    with sqlite3.connect('/data/fixture.db', timeout=5) as db:
                        changed=db.execute('UPDATE event_txn_id_device_id SET inserted_ts=0 WHERE room_id=? AND user_id=? AND device_id=? AND txn_id=?',sys.argv[1:]).rowcount
                        assert changed==1
                        removed=db.execute('DELETE FROM event_txn_id_device_id WHERE inserted_ts < ?', (int(time.time()*1000)-24*60*60*1000,)).rowcount
                        assert removed==1
                    print(removed)
                    """
                        .trimIndent(),
                    roomId,
                    first.userId,
                    first.deviceId,
                    transaction,
                )
            assertEquals(0, cleanup.exitCode, cleanup.stderr)
            assertEquals("1", cleanup.stdout.trim())
            f.restart()
            val current = f.login()
            assertEquals(first.userId, current.userId)
            assertEquals(first.deviceId, current.deviceId)
            val retried = exchange(f, path, current.accessToken, content, "PUT")
            assertEquals(200, retried.statusCode())
            val duplicate = f.json.readTree(retried.body())["event_id"].asString()
            assertNotEquals(eventId, duplicate)
            val history =
                exchange(
                    f,
                    "/_matrix/client/v3/rooms/$roomId/messages?dir=b&limit=20",
                    current.accessToken,
                )
            assertEquals(200, history.statusCode())
            val events =
                f.json
                    .readTree(history.body())["chunk"]
                    .iterator()
                    .asSequence()
                    .filter { it["type"].asString() == "m.room.encrypted" }
                    .toList()
            assertEquals(
                setOf(eventId, duplicate),
                events.map { it["event_id"].asString() }.toSet(),
            )
            assertEquals(2, events.size)
            assertTrue(events.all { it["content"] == f.json.readTree(content) })
            // Whether the provider can still identify the originating client transaction after
            // mapping cleanup matters for the client's uncertainty reconciliation strategy.
            assertTrue(
                events.all { it.path("unsigned").path("transaction_id").asString() == transaction }
            )
            val now = f.clock.instant()
            val peerLogin =
                f.repository.login(
                    f.request.copy(
                        accountId = UUID.randomUUID(),
                        deviceId = UUID.randomUUID(),
                        issuedAt = now,
                        expiresAt = now.plusSeconds(60),
                    )
                )
            assertTrue(peerLogin is Result.Success)
            val peer = (peerLogin as Result.Success).value
            assertEquals(
                200,
                exchange(
                        f,
                        "/_matrix/client/v3/rooms/$roomId/invite",
                        current.accessToken,
                        f.json.writeValueAsBytes(mapOf("user_id" to peer.userId)),
                    )
                    .statusCode(),
            )
            assertEquals(
                200,
                exchange(f, "/_matrix/client/v3/join/$roomId", peer.accessToken, "{}".toByteArray())
                    .statusCode(),
            )
            val peerHistory =
                exchange(
                    f,
                    "/_matrix/client/v3/rooms/$roomId/messages?dir=b&limit=20",
                    peer.accessToken,
                )
            assertEquals(200, peerHistory.statusCode())
            val received =
                f.json
                    .readTree(peerHistory.body())["chunk"]
                    .iterator()
                    .asSequence()
                    .filter { it["type"].asString() == "m.room.encrypted" }
                    .toList()
            assertEquals(
                setOf(eventId, duplicate),
                received.map { it["event_id"].asString() }.toSet(),
            )
            assertTrue(received.all { it.path("unsigned").path("transaction_id").isMissingNode })
        }
    }

    private fun exchange(
        f: MatrixProviderFixture,
        path: String,
        token: String,
        body: ByteArray? = null,
        method: String = if (body == null) "GET" else "POST",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(f.config.origin.resolve(path))
                .timeout(Duration.ofSeconds(2))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .method(
                    method,
                    if (body == null) HttpRequest.BodyPublishers.noBody()
                    else HttpRequest.BodyPublishers.ofByteArray(body),
                )
                .build()
        val pending =
            f.client.sendAsync(request, HttpResponse.BodyHandler { BoundedMatrixBodySubscriber() })
        return try {
            pending.get(2, TimeUnit.SECONDS)
        } finally {
            if (!pending.isDone) pending.cancel(true)
        }
    }
}
