package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixLogin
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterAll
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
import tools.jackson.databind.JsonNode

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_EVENT_READ_APK", matches = ".+")
@Import(
    NativeOriginalDeliveryTraceConfiguration::class,
    NativeSpaceRecoveryTraceConfiguration::class,
    NativeEventReadFailureTraceConfiguration::class,
    NativeSdkErrorTrace::class,
)
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
class NativeSpaceLateKeysTest : NativeSpaceOriginalFixture() {
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var trace: NativeOriginalDeliveryTrace
    @Autowired private lateinit var matrixTrace: NativeSpaceRecoveryTrace
    @Autowired private lateinit var failureTrace: NativeEventReadFailureTrace

    companion object {
        private val provider by lazy { NativeSdkMatrixServer(loginBurst = 32) }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeSpaceLateKeysTest::class
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

    // Each scenario owns fresh provider state, including the finite login budget. Sharing the
    // provider across independent Spring contexts made later scenarios consume earlier logins.
    @AfterEach fun closeScenarioMatrix() = provider.close()

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    @EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_SDK_INCOMING_HINTS", matches = "true")
    fun realKeyDuringLostReceiptReplySurvivesQuietObservationAndOfflineReopen() {
        val base = native()
        val sender = sdkOwner(sdkPeer(base))
        val recipient = sdkOwner(sdkPeer(base))
        val conversation = UUID.randomUUID()
        val room = originalRoom(sender, recipient, conversation)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_late_keys_native.py")) }
        val directory =
            Files.createDirectory(
                Files.createDirectories(root.resolve(".work/collaboration-late-keys"))
                    .resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        write(
            directory,
            "fixture",
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "incomingLateKeys" to true,
                "roomId" to room,
                "conversationId" to conversation,
                "logicalId" to UUID.randomUUID(),
                "secondLogicalId" to UUID.randomUUID(),
                "firstEditId" to UUID.randomUUID(),
                "latestEditId" to UUID.randomUUID(),
                "lateLogicalId" to UUID.randomUUID(),
                "body" to "fictional-hris-worker-late-key-${UUID.randomUUID()}",
                "sender" to sender.session + ("hrisAccessToken" to sender.native.access),
                "recipient" to recipient.session + ("hrisAccessToken" to recipient.native.access),
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
                    root.resolve("tool/collaboration_late_keys_native.py").toString(),
                    "--apk",
                    requireNotNull(System.getenv("HRIS_COLLAB_EVENT_READ_APK")),
                    "--sdk",
                    requireNotNull(System.getenv("ANDROID_HOME")),
                    "--directory",
                    directory.toString(),
                )
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        val phases = linkedMapOf<String, Map<String, Map<String, Int>>>()
        fun record(phase: String) {
            phases[phase] =
                mapOf(
                    "native" to trace.snapshot(),
                    "matrix" to matrixTrace.snapshot(),
                    "nativeKinds" to trace.requestKinds(),
                    "matrixKinds" to matrixTrace.requestKinds(),
                )
        }
        try {
            val ready = waitFor(directory, "ready", process)
            assertPublished(sender, ready["sender"])
            assertPublished(recipient, ready["recipient"])
            trace.arm()
            matrixTrace.arm()
            failureTrace.arm()
            write(directory, "stage-allowed", mapOf("ready" to true))
            val target =
                waitFor(directory, "redaction-request", process)["targetEventId"].asString()
            assertEquals(
                listOf(target),
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId")),
            )
            val redaction = redactFixtureEvent(sender, room, target)
            write(
                directory,
                "redaction-completed",
                mapOf("eventId" to redaction, "targetEventId" to target),
            )
            val staged = waitFor(directory, "staged", process)
            val late = waitFor(directory, "late-key-staged", process)["eventId"].asString()
            val events = staged["events"]
            val originalEvents =
                listOf("original", "peer", "firstEdit", "latestEdit", "redaction").map {
                    events[it].asString()
                }
            assertEquals(6, (originalEvents + late).toSet().size)
            assertEquals(redaction, events["redaction"].asString())
            val senderEvents =
                listOf(
                        events["original"].asString(),
                        events["firstEdit"].asString(),
                        events["latestEdit"].asString(),
                    )
                    .sorted()
            val peerEvents = listOf(events["peer"].asString(), late).sorted()
            assertEquals(
                senderEvents,
                provider.encryptedRoomEvents(room, sender.session.getValue("userId")),
            )
            assertEquals(
                peerEvents,
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId")),
            )
            record("physicalSeedComplete")
            val group = completeOriginal(sender, ready["sender"]["fingerprint"].asString())
            val peerGroup =
                completeOriginal(recipient, ready["recipient"]["fingerprint"].asString())
            assertEquals(group.spaceId, peerGroup.spaceId)
            val canonical = adoptRoom(sender, recipient, group, room)
            write(
                directory,
                "group",
                mapOf(
                    "spaceId" to group.spaceId,
                    "deviceId" to group.deviceId,
                    "bootstrapOperationId" to group.bootstrapOperationId,
                    "registrationId" to group.registrationId,
                    "handoverOperationId" to group.handoverOperationId,
                    "createdAt" to group.createdAt,
                    "conversationId" to canonical,
                ),
            )
            val retained = waitFor(directory, "late-key-ready", process)["lateKeys"]
            for (flag in
                listOf(
                    "committedBeforeLostReply",
                    "keyReceivedDuringHeldReply",
                    "durableBeforeBothAcknowledgements",
                    "firstReceiptNotRewritten",
                    "sameRuntimeInstance",
                    "rawReceiptsDrained",
                )) assertTrue(retained[flag].asBoolean(), flag)
            assertEquals(2, retained["receiptCommits"].asInt())
            assertEquals(2, retained["nativeAcknowledgements"].asInt())
            assertTrue(retained["receiptAttempts"].asInt() in 2..4)
            assertEquals(
                retained["retained"]["receiptDigest"],
                retained["retained"]["requestedGenerationDigest"],
            )
            assertNotEquals(
                retained["retained"]["receiptDigest"],
                retained["retained"]["recoveryGenerationDigest"],
            )
            assertFalse(retained["retained"]["recoveryComplete"].asBoolean())
            record("quietStart")
            matrixTrace.startSyncObservation()
            val started = System.nanoTime()
            write(directory, "late-key-observe", mapOf("ready" to true))
            val quiet = waitFor(directory, "late-key-quiet", process)
            assertTrue(quiet["observedMillis"].asLong() in 30_000..60_000)
            assertTrue(System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(30))
            val quietSync = matrixTrace.finishSyncObservation()
            write(directory, "quiet-sync", quietSync)
            record("quietEnd")
            val before = requireNotNull(phases["quietStart"])
            val after = requireNotNull(phases["quietEnd"])
            assertEquals(before["native"], after["native"])
            assertEquals(before["nativeKinds"], after["nativeKinds"])
            assertEquals(
                requireNotNull(before["matrix"]).filterKeys { it !in setOf("requests", "sync") },
                requireNotNull(after["matrix"]).filterKeys { it !in setOf("requests", "sync") },
            )
            assertEquals(
                requireNotNull(before["matrixKinds"]).filterKeys { it != "sync" },
                requireNotNull(after["matrixKinds"]).filterKeys { it != "sync" },
            )
            val polls =
                requireNotNull(after["matrix"])["sync"]!! -
                    requireNotNull(before["matrix"])["sync"]!!
            assertTrue(polls in 2..4)
            assertEquals(
                polls,
                requireNotNull(after["matrix"])["requests"]!! -
                    requireNotNull(before["matrix"])["requests"]!!,
            )
            assertFalse(quietSync.overflowed)
            assertTrue(quietSync.requests.size in 2..4)
            assertTrue(
                quietSync.requests.all {
                    it.timeoutMillis == 10_000 && it.sincePresent && !it.fullState
                }
            )
            val completedPolls = quietSync.requests.filter { it.elapsedMillis != null }
            assertTrue(completedPolls.isNotEmpty())
            assertTrue(
                completedPolls.all {
                    it.status == 200 && requireNotNull(it.elapsedMillis) in 9_000..20_000
                }
            )
            assertTrue(
                quietSync.requests.zipWithNext().all { (first, next) ->
                    next.startedMillis - first.startedMillis >= 9_000
                }
            )
            write(directory, "late-key-finish", mapOf("ready" to true))
            val adopted = waitFor(directory, "adopted", process)
            assertEquals(retained, adopted["lateKeys"])
            assertTrue(adopted["processCollectorJoined"].asBoolean())
            assertTrue(adopted["runtimeReleased"].asBoolean())
            record("beforeOffline")
            write(directory, "offline-allowed", mapOf("ready" to true))
            val result = waitFor(directory, "result", process)
            assertTrue(process.waitFor(20, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertTrue(result["passed"].asBoolean() && result["cleanup"].asBoolean())
            assertTrue(result["offlineTransportRemoved"].asBoolean())
            val local = result["reopened"]
            assertEquals(retained["retained"], local["retained"])
            assertNotEquals(adopted["processId"].asInt(), local["processId"].asInt())
            assertTrue(
                local["runtimeNeverOpened"].asBoolean() && local["databaseReleased"].asBoolean()
            )
            record("offlineComplete")
            assertEquals(phases["beforeOffline"], phases["offlineComplete"])
            assertTrue(failureTrace.snapshot().isEmpty())
            assertEquals(5, matrixTrace.snapshot()["encryptedSend"])
            assertTrue(requireNotNull(matrixTrace.snapshot()["requests"]) <= 64)
            assertTrue(requireNotNull(trace.snapshot()["requests"]) <= 32)
            assertEquals(
                senderEvents,
                provider.encryptedRoomEvents(room, sender.session.getValue("userId")),
            )
            assertEquals(
                peerEvents,
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId")),
            )
            write(
                directory,
                "accepted",
                mapOf(
                    "encryptedEvents" to 5,
                    "lateKeys" to retained,
                    "quietMillis" to quiet["observedMillis"].asLong(),
                    "quietNonSyncUnchanged" to true,
                    "quietSync" to quietSync,
                    "reopened" to local,
                    "offlineTransportRemoved" to true,
                    "native" to trace.snapshot(),
                    "matrix" to matrixTrace.snapshot(),
                    "failures" to failureTrace.snapshot(),
                ),
            )
        } finally {
            write(directory, "request-phases", phases)
            write(
                directory,
                "request-trace",
                mapOf(
                    "native" to trace.snapshot(),
                    "matrix" to matrixTrace.snapshot(),
                    "failures" to failureTrace.snapshot(),
                    "startupFailures" to failureTrace.startupSnapshot(),
                ),
            )
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                }
            }
            Files.deleteIfExists(directory.resolve("fixture.json"))
        }
    }

    /** Seed provider redaction through the existing fictional identity, not a new HRIS route. */
    private fun redactFixtureEvent(sender: SdkOwner, room: String, target: String): String {
        val at = clock.instant()
        val credential =
            success(
                matrix.login(
                    CollaborationMatrixLogin(
                        sender.native.fixture.company,
                        sender.native.fixture.account,
                        sender.device,
                        at,
                        at.plusSeconds(60),
                    )
                )
            )
        assertEquals(sender.session.getValue("userId"), credential.userId)
        assertEquals(sender.session.getValue("deviceId"), credential.deviceId)
        val path =
            "/_matrix/client/v3/rooms/" +
                URLEncoder.encode(room, StandardCharsets.UTF_8) +
                "/redact/" +
                URLEncoder.encode(target, StandardCharsets.UTF_8) +
                "/" +
                UUID.randomUUID()
        val response =
            sender.native.fixture.browser.send(
                HttpRequest.newBuilder(URI(provider.origin().trimEnd('/') + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${credential.accessToken}")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode())
        assertTrue(response.body().length in 1..16384)
        return json.readTree(response.body())["event_id"].asString().also {
            assertTrue(it.startsWith('$') && it.length in 2..255)
        }
    }

    private fun waitFor(directory: Path, name: String, process: Process): JsonNode {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
        val file = directory.resolve("$name.json")
        while (!Files.isRegularFile(file)) {
            if (!process.isAlive || System.nanoTime() >= until) {
                val result = directory.resolve("result.json")
                val category =
                    if (Files.isRegularFile(result) && Files.size(result) <= 16384)
                        json.readTree(Files.readString(result)).path("failureCategory").asString()
                    else "no_result"
                fail<Nothing>("Event-read phase $name failed: $category")
            }
            Thread.sleep(100)
        }
        assertTrue(Files.size(file) in 1..16384)
        return json.readTree(Files.readString(file))
    }

    private fun write(directory: Path, name: String, value: Any) {
        val target = directory.resolve("$name.json")
        val temporary = directory.resolve("$name.next")
        Files.createFile(
            temporary,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        )
        Files.writeString(temporary, json.writeValueAsString(value))
        Files.move(temporary, target)
    }
}
