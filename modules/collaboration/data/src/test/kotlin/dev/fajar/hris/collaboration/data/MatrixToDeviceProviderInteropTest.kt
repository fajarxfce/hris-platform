package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.BoundedMatrixBodySubscriber
import dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixToDeviceRepository
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

class MatrixToDeviceProviderInteropTest {
    @Test
    fun offlineQueueRetainsEnvelopeAndTokenRotationDeduplicatesButProviderRestartPermitsReplay() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            var sender = f.login()
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            accountId = UUID.randomUUID(),
                            deviceId = UUID.randomUUID(),
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            val from = MatrixKeyFixture(sender.userId, sender.deviceId)
            val to = MatrixKeyFixture(peer.userId, peer.deviceId)
            val envelope =
                CollaborationOlmEnvelope(
                    from.device.keys.getValue("curve25519:${from.deviceId}"),
                    to.device.keys.getValue("curve25519:${to.deviceId}"),
                    "ZmljdGlvbmFsLW9wYXF1ZS1jaXBoZXJ0ZXh0",
                    0,
                    "fixture-olm-message",
                )
            val request =
                CollaborationToDevice(
                    "fixture-transaction",
                    mapOf(peer.userId to mapOf(peer.deviceId to envelope)),
                )
            fun send() =
                InternalCollaborationMatrixToDeviceRepository(
                        HttpMatrixClientDataSource(f.client, f.config, f.clock)
                    )
                    .send(request, sender.accessToken, f.clock.instant().plusSeconds(10))
            fun sync(since: String? = null): tools.jackson.databind.JsonNode {
                val suffix =
                    since?.let { "&since=" + URLEncoder.encode(it, StandardCharsets.UTF_8) } ?: ""
                val response =
                    f.client.send(
                        HttpRequest.newBuilder(
                                f.config.origin.resolve("/_matrix/client/v3/sync?timeout=0$suffix")
                            )
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer ${peer.accessToken}")
                            .GET()
                            .build(),
                        HttpResponse.BodyHandler { BoundedMatrixBodySubscriber() },
                    )
                assertEquals(200, response.statusCode())
                return f.json.readTree(response.body())
            }
            val before = sync().path("next_batch").asString()
            repeat(2) { assertEquals(Result.Success(Unit), send()) }
            sender = f.login()
            assertEquals(Result.Success(Unit), send())
            val first = sync(before)
            val events = first.path("to_device").path("events")
            assertEquals(1, events.size())
            assertEquals(sender.userId, events[0].path("sender").asString())
            assertEquals("m.room.encrypted", events[0].path("type").asString())
            val content = events[0].path("content")
            assertEquals(
                envelope.body,
                content.path("ciphertext").path(envelope.recipientKey).path("body").asString(),
            )
            assertEquals(envelope.messageId, content.path("org.matrix.msgid").asString())
            val acknowledged = sync(first.path("next_batch").asString())
            assertTrue(acknowledged.path("to_device").path("events").isEmpty)
            f.restart()
            assertEquals(Result.Success(Unit), send())
            val replay = sync(acknowledged.path("next_batch").asString())
            assertEquals(1, replay.path("to_device").path("events").size())
            assertEquals(content, replay.path("to_device").path("events")[0].path("content"))
            // Provider cache is process-local. This proves routing/replay, not valid Olm
            // decryption.
        }
    }
}
