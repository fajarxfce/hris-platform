package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixPusherRepository
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class MatrixPusherInteropTest {
    private val first = "hriscp1_" + "a".repeat(64)
    private val second = "hriscp1_" + "b".repeat(64)
    private val gateway =
        MatrixPushGatewayConfiguration(URI("https://push.example.test/_matrix/push/v1/notify"))

    @Test
    fun actualProviderRetainsDevicePushersAcrossRetryRestartAndIndependentRemoval() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val initial = f.login()
            val source = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val repository = InternalCollaborationMatrixPusherRepository(source, gateway)
            repeat(2) {
                assertEquals(
                    Result.Success(Unit),
                    repository.set(first, initial.accessToken, f.clock.instant().plusSeconds(10)),
                )
            }
            val at = f.clock.instant()
            val peer =
                (f.repository.login(
                        f.request.copy(
                            deviceId = UUID.randomUUID(),
                            issuedAt = at,
                            expiresAt = at.plusSeconds(60),
                        )
                    ) as Result.Success)
                    .value
            assertEquals(
                Result.Success(Unit),
                repository.set(second, peer.accessToken, f.clock.instant().plusSeconds(10)),
            )
            val before = pushers(f, initial.accessToken)
            assertEquals(setOf(first, second), before.map { it["pushkey"].asString() }.toSet())
            for (pusher in before) {
                assertEquals("dev.fajar.hris.chat", pusher["app_id"].asString())
                assertEquals("http", pusher["kind"].asString())
                assertEquals(gateway.endpoint.toASCIIString(), pusher["data"]["url"].asString())
                assertEquals("event_id_only", pusher["data"]["format"].asString())
            }
            f.restart()
            val renewed = f.login()
            assertEquals(initial.deviceId, renewed.deviceId)
            assertEquals(
                setOf(first, second),
                pushers(f, renewed.accessToken).map { it["pushkey"].asString() }.toSet(),
            )
            // Rebuilding after restart uses its currently bound origin, never a response URL.
            val resumed =
                InternalCollaborationMatrixPusherRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    gateway,
                )
            assertEquals(
                Result.Success(Unit),
                resumed.set(first, renewed.accessToken, f.clock.instant().plusSeconds(10)),
            )
            repeat(2) {
                assertEquals(
                    Result.Success(Unit),
                    resumed.remove(first, renewed.accessToken, f.clock.instant().plusSeconds(10)),
                )
            }
            assertEquals(
                listOf(second),
                pushers(f, renewed.accessToken).map { it["pushkey"].asString() },
            )
            assertEquals(
                Result.Success(Unit),
                resumed.remove(second, peer.accessToken, f.clock.instant().plusSeconds(10)),
            )
            assertTrue(pushers(f, renewed.accessToken).isEmpty())
        }
    }

    @Test
    fun providerCredentialExpiryDoesNotEraseTheDeviceBinding() {
        MatrixProviderFixture("5s").use { f ->
            f.start()
            val expired = f.login()
            val repository =
                InternalCollaborationMatrixPusherRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    gateway,
                )
            assertEquals(
                Result.Success(Unit),
                repository.set(first, expired.accessToken, f.clock.instant().plusSeconds(4)),
            )
            val wait =
                Duration.between(f.clock.instant(), expired.expiresAt).toMillis().coerceAtLeast(0) +
                    150
            assertTrue(wait in 1..5150)
            Thread.sleep(wait)
            assertEquals(
                "collaboration_provider_rejected",
                (repository.set(first, expired.accessToken, f.clock.instant().plusSeconds(4))
                        as Result.Failed)
                    .failure
                    .code,
            )
            val renewed = f.login()
            assertEquals(expired.deviceId, renewed.deviceId)
            assertEquals(
                listOf(first),
                pushers(f, renewed.accessToken).map { it["pushkey"].asString() },
            )
            assertEquals(
                Result.Success(Unit),
                repository.set(first, renewed.accessToken, f.clock.instant().plusSeconds(4)),
            )
            assertEquals(1, pushers(f, renewed.accessToken).size)
        }
    }

    private fun pushers(f: MatrixProviderFixture, token: String): List<JsonNode> {
        val request =
            HttpRequest.newBuilder(f.config.origin.resolve("/_matrix/client/v3/pushers"))
                .timeout(Duration.ofSeconds(2))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        val response = f.client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, response.statusCode())
        val body = f.json.readTree(response.body())["pushers"]
        assertTrue(body.isArray)
        assertTrue(body.size() <= 2)
        return body.asSequence().toList()
    }
}
