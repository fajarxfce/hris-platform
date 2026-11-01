package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomRepository
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_NATIVE_APK", matches = ".+")
@Import(NativeSdkGatewayTraceConfiguration::class)
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
class NativeCollaborationWorkerInteropTest : NativeGatewaySdkFixture() {
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var rooms: CollaborationMatrixRoomRepository
    @Autowired private lateinit var trace: NativeSdkGatewayTrace

    companion object {
        private val provider by lazy { NativeSdkMatrixServer() }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationWorkerInteropTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }

        @JvmStatic @AfterAll fun closeMatrix() = provider.close()
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    fun actualApplicationWorkerRestoresQueuedIntentAfterProcessDeathAndSendsOnce() {
        val owner = native()
        val sender = sdkOwner(owner)
        val recipient = sdkOwner(sdkPeer(owner))
        val conversation = UUID.randomUUID()
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
        val creation =
            rooms.create(
                CollaborationMatrixDirectRoom(
                    owner.fixture.company,
                    conversation,
                    owner.fixture.account,
                    recipient.native.fixture.account,
                ),
                (login as Result.Success).value.accessToken,
                clock.instant().plusSeconds(20),
            )
        assertInstanceOf(Result.Success::class.java, creation)
        val room =
            ((creation as Result.Success).value as CollaborationMatrixRoomCreation.Created).roomId
        publishSdkRoom(owner, sender.device, recipient.native.fixture.account, room, conversation)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_worker_native.py")) }
        val parent = Files.createDirectories(root.resolve(".work/collaboration-worker"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString().replace("-", "")),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        writePrivate(
            directory.resolve("fixture.json"),
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "sender" to (sender.session + ("hrisAccessToken" to owner.access)),
                "recipient" to recipient.session,
                "roomId" to room,
                "conversationId" to conversation.toString(),
                "logicalId" to UUID.randomUUID().toString(),
                "body" to "fictional-hris-worker-**literal** <tag> 😀\n${UUID.randomUUID()}",
            ),
        )
        runDriver(root, directory)
        val result = json.readTree(Files.readString(directory.resolve("result.json")))
        assertTrue(result.path("passed").asBoolean(), diagnostic(directory))
        assertTrue(result.path("cleanup").asBoolean())
        assertEquals(2, result.path("processes").asInt())
        assertEquals(1, result.path("forcedProcessDeaths").asInt())
        val proof = result.path("proof")
        for (flag in
            listOf(
                "encryptedReceived",
                "senderRuntimeRestoredByWorker",
                "originalHistoryAcquired",
                "completionRetired",
                "repeatedWorkerComplete",
            )) assertTrue(proof.path(flag).asBoolean(), flag)
        assertEquals(
            1,
            trace.snapshot().count {
                it["method"] == "PUT" &&
                    it["route"] == "/_matrix/client/v3/rooms/_/send/m.room.encrypted/_" &&
                    it["status"] == 200
            },
            "The persisted worker intent must send exactly once",
        )
        assertEquals(0, trace.earlyEncryptedSendCount())
        assertEquals(0, trace.applicationAdmissions().getValue("failures"))
        assertEquals(1, trace.applicationSummaryReadCount())
        assertTrue(trace.applicationAdmittedBeforeSend())
        verifyCiphertext(sender, room, proof.path("eventId").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from collaboration_device_key_publications where company_id=? and device_id in (?,?) and version=1",
                    Int::class.java,
                    owner.fixture.company,
                    sender.device,
                    recipient.device,
                ),
        )
        writePrivate(directory.resolve("gateway-trace.json"), trace.snapshot())
        writePrivate(
            directory.resolve("acceptance.json"),
            mapOf(
                "passed" to true,
                "cleanup" to true,
                "processes" to 2,
                "forcedProcessDeaths" to 1,
                "encryptedSends" to 1,
                "applicationAdmissions" to trace.applicationAdmissions(),
                "proof" to json.convertValue(proof, Map::class.java),
                "artifact" to json.convertValue(result.path("artifact"), Map::class.java),
                "platformWorkManagerAccepted" to false,
            ),
        )
    }

    private fun runDriver(root: Path, directory: Path) {
        val log =
            Files.createFile(
                directory.resolve("driver.log"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
        val process =
            ProcessBuilder(
                    "python3",
                    root.resolve("tool/collaboration_worker_native.py").toString(),
                    "--apk",
                    requireNotNull(System.getenv("HRIS_COLLAB_NATIVE_APK")),
                    "--sdk",
                    requireNotNull(System.getenv("ANDROID_HOME")),
                    "--directory",
                    directory.toString(),
                )
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(140)
            while (!Files.exists(directory.resolve("native-worker-restored.json"))) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (System.nanoTime() >= deadline)
                    fail<Nothing>("Owned worker restore exceeded its deadline")
                Thread.sleep(100)
            }
            assertEquals(
                0,
                trace.earlyEncryptedSendCount(),
                "SDK sent before application-worker admission",
            )
            trace.allowEncryptedSends()
            writePrivate(directory.resolve("send.json"), mapOf("ready" to true))
            assertTrue(
                process.waitFor(180, TimeUnit.SECONDS),
                "Owned worker driver exceeded its deadline",
            )
            assertEquals(0, process.exitValue(), diagnostic(directory))
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    assertTrue(
                        process.waitFor(10, TimeUnit.SECONDS),
                        "Owned worker driver did not stop",
                    )
                }
            }
            Files.deleteIfExists(directory.resolve("fixture.json"))
            writePrivate(directory.resolve("gateway-observations.json"), trace.snapshot())
        }
    }

    private fun verifyCiphertext(owner: SdkOwner, room: String, event: String) {
        val response =
            HttpClient.newHttpClient()
                .send(
                    HttpRequest.newBuilder(
                            URI(
                                "http://127.0.0.1:$port/_matrix/client/v3/rooms/${encode(room)}/event/${encode(event)}"
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

    private fun diagnostic(directory: Path): String {
        val path = directory.resolve("result.json")
        if (!Files.isRegularFile(path)) return "Owned worker driver exited without evidence"
        val result = json.readTree(Files.readString(path))
        return "Owned worker proof failed: " +
            result.path("failureCategory").asString() +
            " " +
            result.path("nativeDiagnostic").toString()
    }

    private fun encode(value: String) =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
