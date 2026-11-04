package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
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
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_SPACE_APK", matches = ".+")
@Import(NativeSpaceSdkTraceConfiguration::class)
@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD=1",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationSpaceSdkInteropTest : NativeGatewaySdkFixture() {
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var rooms: CollaborationMatrixRoomRepository
    @Autowired private lateinit var claim: ClaimCollaborationSpaceProvisioning
    @Autowired private lateinit var advance: AdvanceCollaborationSpaceProvisioning
    @Autowired private lateinit var trace: NativeSpaceSdkTrace

    companion object {
        private val provider by lazy { NativeSdkMatrixServer(loginBurst = 32) }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationSpaceSdkInteropTest::class
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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    fun originalQueuedKeysSurviveNativeGroupCompletionAndTwoProcessDeaths(knownPeer: Boolean) {
        val base = native()
        val receiver = sdkOwner(sdkPeer(base))
        val sender = sdkOwner(sdkPeer(base))
        val conversation = UUID.randomUUID()
        val at = clock.instant()
        val credential =
            success(
                matrix.login(
                    CollaborationMatrixLogin(
                        receiver.native.fixture.company,
                        receiver.native.fixture.account,
                        receiver.device,
                        at,
                        at.plusSeconds(60),
                    )
                )
            )
        val created =
            success(
                rooms.create(
                    CollaborationMatrixDirectRoom(
                        receiver.native.fixture.company,
                        conversation,
                        receiver.native.fixture.account,
                        sender.native.fixture.account,
                    ),
                    credential.accessToken,
                    clock.instant().plusSeconds(20),
                )
            )
                as CollaborationMatrixRoomCreation.Created
        val room =
            publishSdkRoom(
                receiver.native,
                receiver.device,
                sender.native.fixture.account,
                created.roomId,
                conversation,
            )
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_space_sdk.py")) }
        val parent = Files.createDirectories(root.resolve(".work/collaboration-space-sdk"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        writePrivate(
            directory.resolve("fixture.json"),
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "receiver" to receiver.session,
                "sender" to sender.session,
                "roomId" to room,
                "knownPeer" to knownPeer,
                "body" to "fictional-space-target-${UUID.randomUUID()}",
                "logicalId" to UUID.randomUUID().toString(),
                "primerBody" to "fictional-space-primer-${UUID.randomUUID()}",
                "primerLogicalId" to UUID.randomUUID().toString(),
            ),
        )
        val log =
            Files.createFile(
                directory.resolve("driver.log"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
        val process =
            ProcessBuilder(
                    "python3",
                    root.resolve("tool/collaboration_space_sdk.py").toString(),
                    "--apk",
                    requireNotNull(System.getenv("HRIS_COLLAB_SPACE_APK")),
                    "--sdk",
                    requireNotNull(System.getenv("ANDROID_HOME")),
                    "--directory",
                    directory.toString(),
                )
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        try {
            val ready = waitFor(directory, "ready", process)
            val fingerprint = ready["fingerprint"].asString()
            assertTrue(fingerprint.matches(Regex("[a-f0-9]{64}")))
            val publicationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (
                database()
                    .queryForObject(
                        "select count(*) from collaboration_device_key_publications where device_id=? and version=1",
                        Int::class.java,
                        receiver.device,
                    ) != 1
            ) {
                assertTrue(
                    process.isAlive && System.nanoTime() < publicationDeadline,
                    "Original SDK keys were not published",
                )
                Thread.sleep(100)
            }
            assertEquals(
                fingerprint,
                database()
                    .queryForObject(
                        "select fingerprint from collaboration_device_key_publications where company_id=? and device_id=? and version=1",
                        String::class.java,
                        receiver.native.fixture.company,
                        receiver.device,
                    ),
            )
            writePrivate(directory.resolve("continue.json"), mapOf("ready" to true))
            val queued = waitFor(directory, "queued", process)
            assertEquals(fingerprint, queued["fingerprint"].asString())
            val hashes =
                provider.encryptedQueue(
                    receiver.session.getValue("userId"),
                    receiver.session.getValue("deviceId"),
                    sender.session.getValue("userId"),
                )
            assertTrue(hashes.isNotEmpty())
            val group = complete(receiver, fingerprint)
            val inventory =
                call(
                    receiver.native,
                    "/api/v1/collaboration/spaces/${group["spaceId"].asString()}/legacy-conversations",
                )
            assertEquals(1, inventory["items"].size())
            assertEquals(room, inventory["items"][0]["room"]["roomId"].asString())
            assertEquals(
                hashes,
                provider.encryptedQueue(
                    receiver.session.getValue("userId"),
                    receiver.session.getValue("deviceId"),
                    sender.session.getValue("userId"),
                ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_devices where account_id=?",
                        Int::class.java,
                        sender.native.fixture.account,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_room_bindings where matrix_room_id=?",
                        Int::class.java,
                        room,
                    ),
            )
            trace.arm(group["accessToken"].asString(), queued["eventId"].asString())
            writePrivate(directory.resolve("group.json"), group)
            waitFor(directory, "lost", process)
            assertTrue(trace.lost())
            val syncsAtLoss = trace.syncs()
            assertTrue(syncsAtLoss in 1..4)
            assertEquals(hashes, trace.hashes())
            assertEquals(
                hashes,
                provider.encryptedQueue(
                    receiver.session.getValue("userId"),
                    receiver.session.getValue("deviceId"),
                    sender.session.getValue("userId"),
                ),
            )
            writePrivate(directory.resolve("restart.json"), mapOf("ready" to true))
            val decrypted = waitFor(directory, "decrypted", process)
            assertEquals(queued["eventId"].asString(), decrypted["eventId"].asString())
            assertEquals(fingerprint, decrypted["fingerprint"].asString())
            assertTrue(decrypted["encrypted"].asBoolean())
            assertTrue(trace.replayed())
            assertEquals(syncsAtLoss + 1, trace.syncs())
            assertTrue(trace.encryptedTarget())
            assertEquals(
                hashes,
                provider.encryptedQueue(
                    receiver.session.getValue("userId"),
                    receiver.session.getValue("deviceId"),
                    sender.session.getValue("userId"),
                ),
            )
            writePrivate(directory.resolve("history.json"), mapOf("ready" to true))
            val result = waitFor(directory, "result", process)
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Owned SDK driver did not terminate")
            assertEquals(0, process.exitValue())
            assertTrue(result["passed"].asBoolean() && result["cleanup"].asBoolean())
            assertTrue(
                provider
                    .encryptedQueue(
                        receiver.session.getValue("userId"),
                        receiver.session.getValue("deviceId"),
                        sender.session.getValue("userId"),
                    )
                    .isEmpty()
            )
            writePrivate(
                directory.resolve("accepted.json"),
                mapOf(
                    "knownPeer" to knownPeer,
                    "driver" to result,
                    "queueHashes" to hashes,
                    "originalProviderFramesRetained" to true,
                    "nativeCompletion" to true,
                    "sameOriginalKeyFingerprint" to true,
                    "actualQueuedKeyReplay" to true,
                    "targetEncryptedOnWire" to true,
                    "syncsAtLoss" to syncsAtLoss,
                    "unmigratedPeer" to true,
                    "originalUnadoptedRoom" to true,
                    "strictShield" to decrypted["strictShield"].asString(),
                    "productAdoption" to false,
                ),
            )
        } finally {
            writePrivate(directory.resolve("server-observation.json"), trace.observation())
            trace.reset()
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                }
            }
            for (name in listOf("fixture.json", "group.json")) Files.deleteIfExists(
                directory.resolve(name)
            )
        }
    }

    private fun complete(owner: SdkOwner, fingerprint: String): JsonNode {
        val native = owner.native
        val current = call(native, "/api/v1/collaboration/spaces/current")
        val base = "/api/v1/collaboration/spaces/${current["spaceId"].asString()}"
        call(native, "$base/identity", emptyMap())
        val registered =
            call(
                native,
                "$base/native-device",
                mapOf(
                    "platform" to "ANDROID",
                    "label" to "Fictional retained SDK",
                    "origin" to "LEGACY",
                    "legacy" to
                        mapOf("companyId" to native.fixture.company, "deviceId" to owner.device),
                    "legacyKeyFingerprint" to fingerprint,
                ),
            )
        val target = UUID.fromString(registered["id"].asString())
        call(
            native,
            "$base/native-device/legacy-runtime/freeze",
            mapOf("deviceId" to target, "expectedDeviceVersion" to 0, "fingerprint" to fingerprint),
        )
        val drain =
            call(
                native,
                "$base/native-device/legacy-runtime/drain?deviceId=$target&expectedDeviceVersion=0&fingerprint=$fingerprint",
            )
        assertEquals(0, drain["activeRequests"].asInt())
        // Fixture-only capability for the explicit worker ports; native commands remain HTTP.
        // Separate API/worker context tests enforce production privilege separation.
        database().execute("grant hris_worker_capability to hris_test_runtime")
        try {
            val leases = success(claim.execute(UUID.randomUUID(), 8))
            assertEquals(listOf(target), leases.map { it.request.deviceId })
            success(advance.execute(leases.single()))
        } finally {
            database().execute("revoke hris_worker_capability from hris_test_runtime")
        }
        val completed =
            database()
                .queryForMap(
                    "select device_id from collaboration_legacy_runtime_completions where legacy_company_id=? and legacy_device_id=?",
                    native.fixture.company,
                    owner.device,
                )
        assertEquals(target, completed["device_id"])
        return call(
            native,
            "$base/native-device/bootstrap",
            mapOf("deviceId" to target, "expectedDeviceVersion" to 1),
        )
    }

    private fun call(n: Native, path: String, body: Map<String, Any>? = null): JsonNode {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
        if (body == null) request.GET()
        else
            request
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        val response = n.fixture.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
        val value = json.readTree(response.body())
        assertEquals(200, response.statusCode()) {
            "Owned group HTTP failed: ${value.path("code").asString()}"
        }
        return value
    }

    private fun <T> success(result: Result<T>): T {
        if (result is Result.Failed)
            fail<Nothing>("Owned SDK fixture failed: ${result.failure.kind}/${result.failure.code}")
        return (result as Result.Success).value
    }

    private fun waitFor(directory: Path, name: String, process: Process): JsonNode {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
        val file = directory.resolve("$name.json")
        while (!Files.isRegularFile(file)) {
            if (!process.isAlive || System.nanoTime() >= until) {
                val result = directory.resolve("result.json")
                val category =
                    if (Files.isRegularFile(result) && Files.size(result) < 65536)
                        json
                            .readTree(Files.readString(result))
                            .path("diagnostic")
                            .path("failureCategory")
                            .asString()
                    else "no_result"
                fail<Nothing>("Owned space SDK phase $name failed: $category")
            }
            Thread.sleep(100)
        }
        assertTrue(Files.size(file) in 1..65536)
        return json.readTree(Files.readString(file))
    }

    private fun writePrivate(file: Path, value: Any) {
        val temporary = file.resolveSibling(file.fileName.toString() + ".next")
        Files.createFile(
            temporary,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        )
        Files.writeString(temporary, json.writeValueAsString(value))
        Files.move(temporary, file)
    }
}
