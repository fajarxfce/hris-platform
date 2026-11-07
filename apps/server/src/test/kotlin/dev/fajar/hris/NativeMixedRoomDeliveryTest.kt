package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomRepository
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_NATIVE_APK", matches = ".+")
@Import(NativeSdkGatewayTraceConfiguration::class, NativeMixedRoomTraceConfiguration::class)
@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
            "hris.test.worker-proof=true",
        ]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NativeMixedRoomDeliveryTest : NativeGatewaySdkFixture() {
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var rooms: CollaborationMatrixRoomRepository
    @Autowired private lateinit var trace: NativeMixedRoomTrace
    @Autowired private lateinit var admissionTrace: NativeSdkGatewayTrace

    companion object {
        private val provider by lazy { NativeSdkMatrixServer() }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeMixedRoomDeliveryTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @AfterEach fun closeMatrix() = provider.close()

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    fun actualWorkerMeasuresReadyRecipientWhileAnotherRoomHasNoPublishedKeys() {
        val native = native()
        val sender = sdkOwner(native)
        val ready = sdkOwner(sdkPeer(native))
        val unavailable = sdkOwner(sdkPeer(native))
        val readyConversation = UUID.randomUUID()
        val unavailableConversation = UUID.randomUUID()
        val readyRoom = room(sender, ready, readyConversation)
        val unavailableRoom = room(sender, unavailable, unavailableConversation)
        trace.track(unavailableRoom, readyRoom)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first {
                    Files.isRegularFile(it.resolve("tool/collaboration_mixed_room_native.py"))
                }
        val parent = Files.createDirectories(root.resolve(".work/collaboration-mixed-room"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        writePrivate(
            directory.resolve("fixture.json"),
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "sender" to (sender.session + ("hrisAccessToken" to native.access)),
                "recipient" to ready.session,
                "roomId" to readyRoom,
                "conversationId" to readyConversation.toString(),
                "logicalId" to UUID.randomUUID().toString(),
                "body" to "fictional-hris-worker-ready-${UUID.randomUUID()}",
                "unavailable" to
                    mapOf(
                        "conversationId" to unavailableConversation.toString(),
                        "logicalId" to UUID.randomUUID().toString(),
                    ),
            ),
        )
        runDriver(root, directory, sender, ready, unavailable)
        val result = json.readTree(Files.readString(directory.resolve("result.json")))
        assertTrue(
            result.path("passed").asBoolean(),
            "Mixed-room proof did not complete; retained private result",
        )
        assertTrue(result.path("cleanup").asBoolean())
        val observed = trace.snapshot()
        assertEquals(1, observed.count { it["room"] == "ready" && it["status"] == 200 })
        assertTrue(
            observed.any {
                it["room"] == "unavailable" &&
                    it["status"] == 403 &&
                    it["errorCode"] == "collaboration_recipient_unavailable"
            }
        )
        assertFalse(observed.any { it["room"] == "unavailable" && it["status"] == 200 })
        assertEquals(1, published(ready))
        assertEquals(0, published(unavailable))
        verifyCiphertext(sender, readyRoom, result.path("proof").path("eventId").asString())
    }

    private fun room(sender: SdkOwner, peer: SdkOwner, conversation: UUID): String {
        val owner = sender.native
        val at = clock.instant()
        val login =
            matrix.login(
                CollaborationMatrixLogin(
                    owner.fixture.company,
                    owner.fixture.account,
                    sender.device,
                    at,
                    at.plusSeconds(60),
                )
            )
        assertInstanceOf(Result.Success::class.java, login)
        val created =
            rooms.create(
                CollaborationMatrixDirectRoom(
                    owner.fixture.company,
                    conversation,
                    owner.fixture.account,
                    peer.native.fixture.account,
                ),
                (login as Result.Success).value.accessToken,
                clock.instant().plusSeconds(20),
            )
        assertInstanceOf(Result.Success::class.java, created)
        val room =
            ((created as Result.Success).value as CollaborationMatrixRoomCreation.Created).roomId
        publishSdkRoom(owner, sender.device, peer.native.fixture.account, room, conversation)
        return room
    }

    private fun published(owner: SdkOwner): Int =
        database()
            .queryForObject(
                "select count(*) from collaboration_device_key_publications p join collaboration_devices d on d.company_id=p.company_id and d.id=p.device_id where p.company_id=? and p.account_id=? and p.device_id=? and p.version=1 and p.published_at is not null and d.state='ACTIVE'",
                Int::class.java,
                owner.native.fixture.company,
                owner.native.fixture.account,
                owner.device,
            ) ?: error("Missing publication count")

    private fun verifyCiphertext(owner: SdkOwner, room: String, event: String) {
        val roomPath = URLEncoder.encode(room, StandardCharsets.UTF_8)
        val eventPath = URLEncoder.encode(event, StandardCharsets.UTF_8)
        val response =
            HttpClient.newHttpClient()
                .send(
                    HttpRequest.newBuilder(
                            URI(
                                "http://127.0.0.1:$port/_matrix/client/v3/rooms/$roomPath/event/$eventPath"
                            )
                        )
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer ${owner.session.getValue("accessToken")}")
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
        assertEquals(200, response.statusCode(), "Owned ciphertext read failed")
        val value = json.readTree(response.body())
        assertEquals("m.room.encrypted", value.path("type").asString())
        assertEquals(owner.session.getValue("userId"), value.path("sender").asString())
        assertFalse(value.path("content").has("body"))
        assertTrue(value.path("content").path("ciphertext").isString)
    }

    private fun runDriver(
        root: Path,
        directory: Path,
        sender: SdkOwner,
        ready: SdkOwner,
        unavailable: SdkOwner,
    ) {
        val process =
            ProcessBuilder(
                    "python3",
                    root.resolve("tool/collaboration_mixed_room_native.py").toString(),
                    "--apk",
                    requireNotNull(System.getenv("HRIS_COLLAB_NATIVE_APK")),
                    "--sdk",
                    requireNotNull(System.getenv("ANDROID_HOME")),
                    "--directory",
                    directory.toString(),
                )
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("driver.log").toFile())
                .start()
        try {
            await(process, 90) { Files.exists(directory.resolve("initialized.json")) }
            await(process, 30) { published(sender) == 1 && published(ready) == 1 }
            assertEquals(0, published(unavailable))
            writePrivate(directory.resolve("readiness.json"), mapOf("ready" to true))
            await(process, 45) { Files.exists(directory.resolve("prepared.json")) }
            assertTrue(trace.snapshot().isEmpty())
            assertEquals(0, admissionTrace.earlyEncryptedSendCount())
            admissionTrace.allowEncryptedSends()
            writePrivate(directory.resolve("start.json"), mapOf("ready" to true))
            await(process, 65) {
                trace.snapshot().any { it["room"] == "unavailable" && it["status"] == 403 }
            }
            writePrivate(directory.resolve("enqueue.json"), mapOf("ready" to true))
            assertTrue(
                process.waitFor(180, TimeUnit.SECONDS),
                "Mixed-room driver exceeded deadline",
            )
            assertEquals(
                0,
                process.exitValue(),
                "Mixed-room driver failed; retained private result",
            )
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                }
            }
            Files.deleteIfExists(directory.resolve("fixture.json"))
            writePrivate(directory.resolve("gateway-observations.json"), trace.snapshot())
            writePrivate(directory.resolve("gateway-protocol.json"), admissionTrace.snapshot())
        }
    }

    private fun await(process: Process, seconds: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (!condition()) {
            assertTrue(process.isAlive, "Mixed-room driver stopped; retained private result")
            assertTrue(System.nanoTime() < deadline, "Mixed-room fixture deadline exceeded")
            Thread.sleep(100)
        }
    }

    private fun writePrivate(path: Path, value: Any) {
        val temporary = path.resolveSibling(path.fileName.toString() + ".next")
        Files.createFile(
            temporary,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        )
        try {
            Files.writeString(temporary, json.writeValueAsString(value))
            Files.move(temporary, path)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
