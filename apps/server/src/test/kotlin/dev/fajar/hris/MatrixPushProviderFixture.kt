package dev.fajar.hris

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.testcontainers.DockerClientFactory
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import tools.jackson.databind.json.JsonMapper

/** Own pinned Synapse/TLS proxy processes, network and ephemeral CA; never disables TLS checks. */
class MatrixPushProviderFixture : AutoCloseable {
    private val json = JsonMapper.builder().build()
    private val network = Network.newNetwork()
    private val root =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("deploy/synapse/push-gateway.nginx.conf")) }
    val signingFile: Path =
        Path.of(requireNotNull(javaClass.getResource("/matrix-signing.fixture")).toURI())
    private val certificate = CertificateMaterial()
    private var proxy: GenericContainer<*>? = null
    private val synapse =
        GenericContainer(
                "ghcr.io/element-hq/synapse:v1.164.1@sha256:fa4aa980f279bcf26d4e7ecc2a2c9a701c73f2ebe8841da9bdcf3b2fdf693d75"
            )
            .withNetwork(network)
            .withNetworkAliases("hris-synapse-fixture")
            .withExposedPorts(8008)
            .withEnv("PYTHONPATH", "/data")
            .withEnv("SSL_CERT_FILE", "/data/push-ca.pem")
            .withCopyToContainer(Transferable.of(certificate.caPem), "/data/push-ca.pem")
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
                                    "secret" to Files.readString(signingFile).trim(),
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
                            // Only the isolated fixture network; production must list its exact
                            // private proxy peers.
                            "ip_range_whitelist" to
                                listOf("172.16.0.0/12", "10.0.0.0/8", "192.168.0.0/16"),
                            "log_config" to "/data/logging.json",
                        )
                    )
                ),
                "/data/homeserver.yaml",
            )
            .apply {
                for (name in listOf("logging.json", "hris_push_logging.py")) withCopyToContainer(
                    Transferable.of(Files.readAllBytes(root.resolve("deploy/synapse/$name"))),
                    "/data/$name",
                )
            }
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

    val client: HttpClient =
        HttpClient.newBuilder()
            .sslContext(certificate.context)
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    init {
        try {
            synapse.start()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    val origin: URI
        get() = URI("http://127.0.0.1:${port(synapse, 8008)}")

    val callback: String = "https://hris-push-fixture:9443/_matrix/push/v1/notify"
    val tlsOrigin: URI
        get() = URI("https://localhost:${port(requireNotNull(proxy), 9443)}")

    val publicOrigin: URI
        get() = URI("http://127.0.0.1:${port(requireNotNull(proxy), 8080)}")

    fun startProxy(apiPort: Int) {
        check(proxy == null)
        Testcontainers.exposeHostPorts(apiPort)
        val server =
            GenericContainer(
                    "nginx@sha256:0e52bd054126cdc040b1276c0af347aeb02a41b9cfe305d6094767ac30e97949"
                )
                .withNetwork(network)
                .withNetworkAliases("hris-push-fixture")
                .withExposedPorts(9443, 8080)
                .withAccessToHost(true)
                .withCopyToContainer(
                    Transferable.of(
                        Files.readAllBytes(root.resolve("deploy/synapse/push-gateway.nginx.conf"))
                    ),
                    "/etc/nginx/nginx.conf",
                )
                .withCopyToContainer(
                    Transferable.of(
                        Files.readAllBytes(
                            root.resolve("deploy/synapse/push-public-deny.nginx.conf")
                        )
                    ),
                    "/etc/nginx/push-public-deny.conf",
                )
                .withCopyToContainer(
                    Transferable.of("server host.testcontainers.internal:$apiPort;"),
                    "/etc/nginx/hris-push-upstream.conf",
                )
                .withCopyToContainer(
                    Transferable.of(
                        "allow 172.16.0.0/12; allow 10.0.0.0/8; allow 192.168.0.0/16; allow 127.0.0.1; deny all;"
                    ),
                    "/etc/nginx/hris-push-sources.conf",
                )
                .withCopyToContainer(
                    Transferable.of(
                        "server { listen 8080; include /etc/nginx/push-public-deny.conf; location / { return 404; } }"
                    ),
                    "/etc/nginx/hris-push-listeners/public-proof.conf",
                )
                .withCopyToContainer(
                    Transferable.of(certificate.leafPem),
                    "/run/secrets/push-tls.crt",
                )
                .withCopyToContainer(
                    Transferable.of(certificate.keyPem),
                    "/run/secrets/push-tls.key",
                )
                .withCreateContainerCmdModifier { command ->
                    command.withEntrypoint("nginx")
                    command.hostConfig!!
                        .withPortBindings(
                            *listOf(9443, 8080)
                                .map {
                                    PortBinding(
                                        Ports.Binding.bindIpAndPort("127.0.0.1", 0),
                                        ExposedPort(it),
                                    )
                                }
                                .toTypedArray()
                        )
                        .withMemory(128L * 1024 * 1024)
                        .withNanoCPUs(500_000_000L)
                }
                .withCommand("-g", "daemon off;")
                .waitingFor(
                    Wait.forHttp("/ready")
                        .forPort(8080)
                        .forStatusCode(404)
                        .withStartupTimeout(Duration.ofSeconds(30))
                )
        proxy = server
        try {
            server.start()
        } catch (error: Throwable) {
            server.close()
            proxy = null
            throw error
        }
    }

    fun stopProxy() {
        proxy?.close()
        proxy = null
    }

    /** Fixed bounded metadata probe: no keys, event bodies or bearer material leave SQLite. */
    fun providerHasFailedPush(): Boolean {
        val result =
            synapse.execInContainer(
                "timeout",
                "5",
                "python",
                "-c",
                "import sqlite3; c=sqlite3.connect('/data/fixture.db',timeout=2); print(int(c.execute('select count(*) from pushers where failing_since is not null').fetchone()[0]>0))",
            )
        check(result.exitCode == 0) { "Provider push state probe failed" }
        return result.stdout.trim() == "1"
    }

    fun pusherCount(): Int {
        val result =
            synapse.execInContainer(
                "timeout",
                "5",
                "python",
                "-c",
                "import sqlite3; c=sqlite3.connect('/data/fixture.db',timeout=2); print(c.execute('select count(*) from pushers').fetchone()[0])",
            )
        check(result.exitCode == 0) { "Provider pusher count probe failed" }
        return result.stdout.trim().toInt()
    }

    private fun port(container: GenericContainer<*>, port: Int): Int {
        val info =
            DockerClientFactory.instance()
                .client()
                .inspectContainerCmd(container.containerId)
                .exec()
        return requireNotNull(info.networkSettings.ports.bindings[ExposedPort(port)])
            .single()
            .hostPortSpec
            .toInt()
    }

    override fun close() {
        try {
            stopProxy()
        } finally {
            try {
                synapse.close()
            } finally {
                try {
                    network.close()
                } finally {
                    client.shutdownNow()
                }
            }
        }
    }

    private class CertificateMaterial {
        private val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        private val ca = generator.generateKeyPair()
        private val leaf = generator.generateKeyPair()
        private val issuer = X500Name("CN=HRIS owned push fixture CA")
        private val before = Date.from(Instant.now().minusSeconds(60))
        private val after = Date.from(Instant.now().plusSeconds(86400))

        private fun serial() = BigInteger(128, SecureRandom()).add(BigInteger.ONE)

        private val caCertificate =
            JcaX509CertificateConverter()
                .getCertificate(
                    JcaX509v3CertificateBuilder(issuer, serial(), before, after, issuer, ca.public)
                        .addExtension(Extension.basicConstraints, true, BasicConstraints(0))
                        .addExtension(
                            Extension.keyUsage,
                            true,
                            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
                        )
                        .build(JcaContentSignerBuilder("SHA256withRSA").build(ca.private))
                )
        private val certificate =
            JcaX509CertificateConverter()
                .getCertificate(
                    JcaX509v3CertificateBuilder(
                            issuer,
                            serial(),
                            before,
                            after,
                            X500Name("CN=hris-push-fixture"),
                            leaf.public,
                        )
                        .addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                        .addExtension(
                            Extension.keyUsage,
                            true,
                            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
                        )
                        .addExtension(
                            Extension.extendedKeyUsage,
                            false,
                            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
                        )
                        .addExtension(
                            Extension.subjectAlternativeName,
                            false,
                            GeneralNames(
                                arrayOf(
                                    GeneralName(GeneralName.dNSName, "hris-push-fixture"),
                                    GeneralName(GeneralName.dNSName, "localhost"),
                                )
                            ),
                        )
                        .build(JcaContentSignerBuilder("SHA256withRSA").build(ca.private))
                )
        val caPem = pem("CERTIFICATE", caCertificate.encoded)
        val leafPem = pem("CERTIFICATE", certificate.encoded)
        val keyPem = pem("PRIVATE KEY", leaf.private.encoded)
        val context =
            SSLContext.getInstance("TLS").apply {
                val store =
                    KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                        load(null)
                        setCertificateEntry("fixture", caCertificate)
                    }
                val trusts =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        .apply { init(store) }
                init(null, trusts.trustManagers, SecureRandom())
            }

        private fun pem(type: String, bytes: ByteArray) =
            "-----BEGIN $type-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes) +
                "\n-----END $type-----\n"
    }
}
