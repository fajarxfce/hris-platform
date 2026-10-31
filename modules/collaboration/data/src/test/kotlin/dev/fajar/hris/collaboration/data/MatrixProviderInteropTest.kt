package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.*
import java.util.*
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Real pinned Synapse; no HRIS/Firebase identities or running application infrastructure. */
class MatrixProviderInteropTest {
    @Test
    fun boundedInternalTokensExpireWithoutDeletingKeysButLogoutDeletesTheEntireDevice() {
        MatrixProviderFixture("5s").use { fixture ->
            fixture.start()
            val json = fixture.json
            val client = fixture.client
            val config = fixture.config
            val origin = config.origin
            val repository = fixture.repository
            val clock = fixture.clock
            val request = fixture.request
            fun login() = fixture.login()
            val first = login()
            assertEquals(
                "@${collaborationMatrixLocalpart(request.companyId, request.accountId)}:${config.serverName}",
                first.userId,
            )
            assertEquals(collaborationMatrixDeviceId(request.deviceId), first.deviceId)
            assertTrue(first.expiresAt <= clock.instant().plusSeconds(5))
            val deviceKeys = deviceKeys(json, first.userId, first.deviceId)
            assertEquals(
                200,
                exchange(
                        client,
                        origin,
                        "/_matrix/client/v3/keys/upload",
                        first.accessToken,
                        json.writeValueAsBytes(mapOf("device_keys" to deviceKeys)),
                    )
                    .statusCode(),
            )
            // A lost login receipt may create another bearer for the same existing E2EE device.
            Thread.sleep(3000)
            val second = login()
            assertNotEquals(first.accessToken, second.accessToken)
            val waitMillis =
                Duration.between(clock.instant(), first.expiresAt.plusMillis(350)).toMillis()
            if (waitMillis > 0) Thread.sleep(waitMillis.coerceAtMost(6000))
            assertEquals(
                401,
                exchange(client, origin, "/_matrix/client/v3/account/whoami", first.accessToken)
                    .statusCode(),
            )
            assertEquals(
                200,
                exchange(client, origin, "/_matrix/client/v3/account/whoami", second.accessToken)
                    .statusCode(),
            )
            val queried =
                exchange(
                    client,
                    origin,
                    "/_matrix/client/v3/keys/query",
                    second.accessToken,
                    json.writeValueAsBytes(
                        mapOf("device_keys" to mapOf(first.userId to listOf(first.deviceId)))
                    ),
                )
            assertEquals(200, queried.statusCode())
            val retained =
                json
                    .readTree(queried.body())
                    .path("device_keys")
                    .path(first.userId)
                    .path(first.deviceId)
                    .path("keys")
            assertEquals(json.valueToTree<JsonNode>(deviceKeys["keys"]), retained)
            // Deliberately exercise permanent retirement at the end of this fictional device's
            // life.
            // Even the EXPIRED first bearer deletes the replacement session and all device keys.
            assertEquals(Result.Success(Unit), repository.logoutDevice(first.accessToken))
            assertEquals(
                401,
                exchange(client, origin, "/_matrix/client/v3/account/whoami", second.accessToken)
                    .statusCode(),
            )
            assertEquals(Result.Success(Unit), repository.logoutDevice(first.accessToken))
            val replacement = login()
            val cleared =
                exchange(
                    client,
                    origin,
                    "/_matrix/client/v3/keys/query",
                    replacement.accessToken,
                    json.writeValueAsBytes(
                        mapOf("device_keys" to mapOf(first.userId to listOf(first.deviceId)))
                    ),
                )
            assertEquals(200, cleared.statusCode())
            assertTrue(
                json
                    .readTree(cleared.body())
                    .path("device_keys")
                    .path(first.userId)
                    .path(first.deviceId)
                    .isMissingNode
            )
            assertEquals(Result.Success(Unit), repository.logoutDevice(replacement.accessToken))
        }
    }

    private fun exchange(
        client: HttpClient,
        origin: URI,
        path: String,
        token: String,
        body: ByteArray? = null,
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(origin.resolve(path))
                .timeout(Duration.ofSeconds(2))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
        if (body == null) request.GET()
        else request.POST(HttpRequest.BodyPublishers.ofByteArray(body))
        val pending =
            client.sendAsync(
                request.build(),
                HttpResponse.BodyHandler { BoundedMatrixBodySubscriber() },
            )
        return try {
            pending.get(2, TimeUnit.SECONDS)
        } finally {
            if (!pending.isDone) pending.cancel(true)
        }
    }

    private fun deviceKeys(json: JsonMapper, user: String, device: String): Map<String, Any> {
        val signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val curve = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val encoding = Base64.getEncoder().withoutPadding()
        val value =
            sortedMapOf<String, Any>(
                "algorithms" to listOf("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2"),
                "device_id" to device,
                "user_id" to user,
                "keys" to
                    sortedMapOf(
                        "curve25519:$device" to
                            encoding.encodeToString(
                                curve.public.encoded.takeLast(32).toByteArray()
                            ),
                        "ed25519:$device" to
                            encoding.encodeToString(
                                signing.public.encoded.takeLast(32).toByteArray()
                            ),
                    ),
            )
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(signing.private)
        signature.update(json.writeValueAsBytes(value))
        value["signatures"] =
            mapOf(user to mapOf("ed25519:$device" to encoding.encodeToString(signature.sign())))
        return value
    }
}
