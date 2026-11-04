package dev.fajar.hris

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import java.time.Duration
import java.util.UUID
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.json.JsonMapper

/** Only fictional credentials and a private loopback provider; no production configuration. */
class NativeSdkMatrixServer(messageBurst: Int = 10, loginBurst: Int? = null) : AutoCloseable {
    init {
        require(messageBurst in 10..256)
        require(loginBurst == null || loginBurst in 5..32)
    }

    private val json = JsonMapper.builder().build()
    private val secret =
        requireNotNull(javaClass.getResourceAsStream("/matrix-signing.fixture"))
            .bufferedReader()
            .use { it.readText().trim() }
    private val server =
        GenericContainer(
                "ghcr.io/element-hq/synapse:v1.164.1@sha256:fa4aa980f279bcf26d4e7ecc2a2c9a701c73f2ebe8841da9bdcf3b2fdf693d75"
            )
            .withExposedPorts(8008)
            .withCopyToContainer(
                Transferable.of(
                    json.writeValueAsBytes(
                        mapOf(
                                "server_name" to "matrix.example.test",
                                "report_stats" to false,
                                "enable_registration" to false,
                                // Only the bounded historical-message fixture opts into a larger
                                // burst.
                                "rc_message" to
                                    mapOf("per_second" to 0.2, "burst_count" to messageBurst),
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
                            )
                            .let { config ->
                                // The multi-session migration experiment opts into a finite burst.
                                // Defaults remain the pinned Synapse defaults for existing
                                // scenarios.
                                if (loginBurst == null) config
                                else
                                    config +
                                        mapOf(
                                            "rc_login" to
                                                mapOf(
                                                    "address" to
                                                        mapOf(
                                                            "per_second" to 0.003,
                                                            "burst_count" to loginBurst,
                                                        ),
                                                    "account" to
                                                        mapOf(
                                                            "per_second" to 0.003,
                                                            "burst_count" to loginBurst,
                                                        ),
                                                )
                                        )
                            }
                    )
                ),
                "/data/homeserver.yaml",
            )
            .withCreateContainerCmdModifier {
                it.withEntrypoint("python", "-m", "synapse.app.homeserver")
                it.hostConfig!!
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

    fun origin(): String {
        if (!server.isRunning) server.start()
        return "http://127.0.0.1:${server.getMappedPort(8008)}"
    }

    /** Read-only fixture observation, never exposing encrypted frames or provider credentials. */
    fun encryptedQueue(userId: String, deviceId: String, sender: String): List<String> {
        require(userId.startsWith("@hris_") && sender.startsWith("@hris_"))
        require(deviceId.matches(Regex("HRIS_[a-f0-9]{32}")))
        val probe =
            server.execInContainer(
                "python",
                "-c",
                """
                import hashlib,json,sqlite3,sys
                with sqlite3.connect('file:/data/fixture.db?mode=ro',uri=True,timeout=5) as db:
                 rows=db.execute('select message_json from device_inbox where user_id=? and device_id=? limit 65',(sys.argv[1],sys.argv[2])).fetchall()
                 assert len(rows)<=64
                 events=[json.loads(row[0]) for row in rows]
                 selected=[event for event in events if event.get('type')=='m.room.encrypted' and event.get('sender')==sys.argv[3]]
                 print(json.dumps(sorted(hashlib.sha256(json.dumps(event,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest() for event in selected)))
                """
                    .trimIndent(),
                userId,
                deviceId,
                sender,
            )
        check(probe.exitCode == 0 && probe.stdout.length <= 5000)
        val values =
            json.readTree(probe.stdout).iterator().asSequence().map { it.asString() }.toList()
        check(values.size <= 64 && values.all { it.matches(Regex("[a-f0-9]{64}")) })
        return values
    }

    override fun close() {
        server.stop()
    }
}
