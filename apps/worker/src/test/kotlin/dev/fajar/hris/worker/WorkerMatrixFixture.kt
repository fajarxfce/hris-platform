package dev.fajar.hris.worker

import com.github.dockerjava.api.model.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.json.JsonMapper

/** Owned pinned Synapse and private signing file. No real HRIS account/provider is accessed. */
class WorkerMatrixFixture :
    GenericContainer<WorkerMatrixFixture>(
        "ghcr.io/element-hq/synapse:v1.164.1@sha256:fa4aa980f279bcf26d4e7ecc2a2c9a701c73f2ebe8841da9bdcf3b2fdf693d75"
    ) {
    private var networkOwner: Network? = null
    private var directory: Path? = null
    val signingFile: Path
        get() = requireNotNull(directory).resolve("signing")

    val origin: String
        get() = "http://127.0.0.1:${getMappedPort(8008)}"

    override fun start() {
        val network = Network.newNetwork()
        networkOwner = network
        try {
            directory =
                Files.createTempDirectory(
                    "hris-owned-matrix-",
                    PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")
                    ),
                )
            val secret = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            Files.writeString(
                Files.createFile(
                    signingFile,
                    PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")
                    ),
                ),
                secret,
            )
            withNetwork(network)
            withExposedPorts(8008)
            withCopyToContainer(
                Transferable.of(
                    JsonMapper.builder()
                        .build()
                        .writeValueAsBytes(
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
                                "nonrefreshable_access_token_lifetime" to "10m",
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
            withCreateContainerCmdModifier { command ->
                command.withEntrypoint("python", "-m", "synapse.app.homeserver")
                command.hostConfig!!
                    .withPortBindings(
                        PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort(8008))
                    )
                    .withMemory(1024L * 1024 * 1024)
                    .withNanoCPUs(2_000_000_000L)
            }
            withCommand("--config-path", "/data/homeserver.yaml")
            waitingFor(
                Wait.forHttp("/_matrix/client/versions")
                    .forPort(8008)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
            super.start()
        } catch (error: Throwable) {
            try {
                stop()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    override fun stop() {
        try {
            super.stop()
        } finally {
            try {
                networkOwner?.close()
            } finally {
                directory?.let {
                    Files.deleteIfExists(it.resolve("signing"))
                    Files.deleteIfExists(it)
                }
                directory = null
                networkOwner = null
            }
        }
    }
}
