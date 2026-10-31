package dev.fajar.hris.collaboration.data

import com.github.dockerjava.api.model.*
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixLogin
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.*
import java.time.*
import java.util.*
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.json.JsonMapper

/** Owned pinned provider process; each test gets its own network, database and HTTP owner. */
class MatrixProviderFixture(lifetime: String) : AutoCloseable {
    init {
        require(lifetime in setOf("5s", "10m"))
    }

    val json = JsonMapper.builder().build()
    private val secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    private val network = Network.newNetwork()
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
                            "nonrefreshable_access_token_lifetime" to lifetime,
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
                        PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort(8008))
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
    val clock = Clock.systemUTC()
    lateinit var config: MatrixProviderConfiguration
        private set

    lateinit var repository: InternalCollaborationMatrixRepository
        private set

    val request =
        CollaborationMatrixLogin(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            clock.instant(),
            clock.instant().plusSeconds(60),
        )

    fun start() {
        server.start()
        configure()
    }

    private fun configure() {
        val inspected =
            org.testcontainers.DockerClientFactory.instance()
                .client()
                .inspectContainerCmd(server.containerId)
                .exec()
        val port =
            requireNotNull(inspected.networkSettings.ports.bindings[ExposedPort(8008)])
                .single()
                .hostPortSpec
                .toInt()
        config =
            MatrixProviderConfiguration(
                URI("http://127.0.0.1:$port"),
                "matrix.example.test",
                "urn:hris:matrix:fixture",
                "matrix:fixture",
                secret,
            )
        repository =
            InternalCollaborationMatrixRepository(
                HmacMatrixJwtDataSource(config),
                HttpMatrixClientDataSource(client, config, clock),
                clock,
            )
    }

    fun login(): dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixCredential {
        val at = clock.instant()
        val result = repository.login(request.copy(issuedAt = at, expiresAt = at.plusSeconds(60)))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    fun restart() {
        org.testcontainers.DockerClientFactory.instance()
            .client()
            .restartContainerCmd(server.containerId)
            .withTimeout(3)
            .exec()
        configure()
        val until = System.nanoTime() + Duration.ofSeconds(15).toNanos()
        while (System.nanoTime() < until) {
            val pending =
                client.sendAsync(
                    HttpRequest.newBuilder(config.origin.resolve("/_matrix/client/versions"))
                        .timeout(Duration.ofMillis(500))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            try {
                if (pending.get(500, TimeUnit.MILLISECONDS).statusCode() == 200) return
            } catch (error: Exception) {
                if (
                    error is InterruptedException ||
                        error is java.util.concurrent.CancellationException
                )
                    throw error
            } finally {
                if (!pending.isDone) pending.cancel(true)
            }
            Thread.sleep(100)
        }
        error("Owned Matrix provider did not restart before its deadline")
    }

    override fun close() {
        client.shutdownNow()
        try {
            server.close()
        } finally {
            network.close()
        }
        try {
            assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        }
    }
}
