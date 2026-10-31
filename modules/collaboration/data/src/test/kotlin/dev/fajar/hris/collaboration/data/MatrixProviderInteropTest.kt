package dev.fajar.hris.collaboration.data

import com.github.dockerjava.api.model.*
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixLogin
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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Real pinned Synapse; no HRIS/Firebase identities or running application infrastructure. */
class MatrixProviderInteropTest {
    @Test
    fun boundedInternalTokensExpireWithoutDeletingKeysButLogoutDeletesTheEntireDevice() {
        val json = JsonMapper.builder().build()
        val secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val network = Network.newNetwork()
        val server =
            GenericContainer(
                    "ghcr.io/element-hq/synapse:v1.164.1@sha256:fa4aa980f279bcf26d4e7ecc2a2c9a701c73f2ebe8841da9bdcf3b2fdf693d75"
                )
                .withNetwork(network)
                .withExposedPorts(8008)
                .withCopyToContainer(
                    Transferable.of(
                        json.writeValueAsBytes(
                            mapOf(
                                "server_name" to "matrix.example.test",
                                "report_stats" to false,
                                "enable_registration" to false,
                                "password_config" to mapOf("enabled" to false),
                                "jwt_config" to
                                    mapOf(
                                        "enabled" to true,
                                        "secret" to secret,
                                        "algorithm" to "HS256",
                                        "issuer" to "urn:hris:matrix:fixture",
                                        "audiences" to listOf("matrix:fixture"),
                                    ),
                                "nonrefreshable_access_token_lifetime" to "5s",
                                "listeners" to
                                    listOf(
                                        mapOf(
                                            "port" to 8008,
                                            "type" to "http",
                                            "tls" to false,
                                            "bind_addresses" to listOf("0.0.0.0"),
                                            "resources" to
                                                listOf(
                                                    mapOf(
                                                        "names" to listOf("client"),
                                                        "compress" to false,
                                                    )
                                                ),
                                        )
                                    ),
                                "database" to
                                    mapOf(
                                        "name" to "sqlite3",
                                        "args" to mapOf("database" to "/data/fixture.db"),
                                    ),
                                "media_store_path" to "/data/media",
                                "signing_key_path" to "/data/signing.key",
                                "macaroon_secret_key" to UUID.randomUUID().toString(),
                                "form_secret" to UUID.randomUUID().toString(),
                                "federation_domain_whitelist" to emptyList<String>(),
                                "trusted_key_servers" to emptyList<String>(),
                                "suppress_key_server_warning" to true,
                                "url_preview_enabled" to false,
                                "enable_metrics" to false,
                            )
                        )
                    ),
                    "/data/homeserver.yaml",
                )
                .withCreateContainerCmdModifier { command ->
                    command.withEntrypoint("python", "-m", "synapse.app.homeserver")
                    command.hostConfig!!
                        .withPortBindings(
                            PortBinding(
                                Ports.Binding.bindIpAndPort("127.0.0.1", 0),
                                ExposedPort(8008),
                            )
                        )
                        .withMemory(1024L * 1024 * 1024)
                        .withNanoCPUs(2_000_000_000L)
                }
                .withCommand("--config-path", "/data/homeserver.yaml")
                .waitingFor(
                    Wait.forHttp("/_matrix/client/versions")
                        .forPort(8008)
                        .withStartupTimeout(Duration.ofMinutes(2))
                )
        val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        try {
            server.start()
            val origin = URI("http://127.0.0.1:${server.getMappedPort(8008)}")
            val config =
                MatrixProviderConfiguration(
                    origin,
                    "matrix.example.test",
                    "urn:hris:matrix:fixture",
                    "matrix:fixture",
                    secret,
                )
            val clock = Clock.systemUTC()
            val repository =
                InternalCollaborationMatrixRepository(
                    HmacMatrixJwtDataSource(config),
                    HttpMatrixClientDataSource(client, config, clock),
                    clock,
                )
            val request =
                CollaborationMatrixLogin(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    clock.instant(),
                    clock.instant().plusSeconds(60),
                )
            fun login():
                dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixCredential {
                val at = clock.instant()
                return (repository.login(
                        request.copy(issuedAt = at, expiresAt = at.plusSeconds(60))
                    ) as Result.Success)
                    .value
            }
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
        } finally {
            client.shutdownNow()
            try {
                server.close()
            } finally {
                network.close()
            }
            assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
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
