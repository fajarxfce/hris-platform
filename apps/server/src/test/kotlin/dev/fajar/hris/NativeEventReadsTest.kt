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

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
class NativeEventReadsTest : NativeSpaceOriginalFixture() {
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
                                NativeEventReadsTest::class
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
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    fun exactEncryptedEventsSurviveOriginalRuntimeAdoptionWithoutPresentationRelabeling() {
        val base = native()
        val sender = sdkOwner(sdkPeer(base))
        val recipient = sdkOwner(sdkPeer(base))
        val conversation = UUID.randomUUID()
        val room = originalRoom(sender, recipient, conversation)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first {
                    Files.isRegularFile(it.resolve("tool/collaboration_event_reads_native.py"))
                }
        val parent = Files.createDirectories(root.resolve(".work/collaboration-event-reads"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        write(
            directory,
            "fixture",
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "roomId" to room,
                "conversationId" to conversation,
                "logicalId" to UUID.randomUUID(),
                "secondLogicalId" to UUID.randomUUID(),
                "firstEditId" to UUID.randomUUID(),
                "latestEditId" to UUID.randomUUID(),
                "body" to "fictional-hris-worker-events-${UUID.randomUUID()}",
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
                    root.resolve("tool/collaboration_event_reads_native.py").toString(),
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
        try {
            val ready = waitFor(directory, "ready", process)
            assertPublished(sender, ready["sender"])
            assertPublished(recipient, ready["recipient"])
            trace.arm()
            matrixTrace.arm()
            failureTrace.arm()
            write(directory, "stage-allowed", mapOf("ready" to true))
            val redaction = waitFor(directory, "redaction-request", process)
            val target = redaction["targetEventId"].asString()
            assertEquals(
                listOf(target),
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId")),
            )
            val redactionId = redactFixtureEvent(sender, room, target)
            write(
                directory,
                "redaction-completed",
                mapOf("eventId" to redactionId, "targetEventId" to target),
            )
            val staged = waitFor(directory, "staged", process)
            for (flag in
                listOf(
                    "bothSdkClosed",
                    "missingKeyObserved",
                    "decryptedAfterSync",
                    "redactedTargetSenderPreserved",
                )) assertTrue(staged[flag].asBoolean(), flag)
            val events = staged["events"]
            assertEquals(redactionId, events["redaction"].asString())
            assertEquals(
                5,
                listOf("original", "peer", "firstEdit", "latestEdit", "redaction")
                    .map { events[it].asString() }
                    .toSet()
                    .size,
            )
            val senderEvents = provider.encryptedRoomEvents(room, sender.session.getValue("userId"))
            val recipientEvents =
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId"))
            assertEquals(
                setOf(
                    events["original"].asString(),
                    events["firstEdit"].asString(),
                    events["latestEdit"].asString(),
                ),
                senderEvents.toSet(),
            )
            assertEquals(listOf(events["peer"].asString()), recipientEvents)
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
            val result = waitFor(directory, "result", process)
            assertTrue(process.waitFor(20, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertTrue(result["passed"].asBoolean())
            assertTrue(result["cleanup"].asBoolean())
            val adopted = result["adopted"]
            assertTrue(result["offlineTransportRemoved"].asBoolean())
            val incoming = adopted["incoming"]
            val reopened = result["reopened"]
            assertTrue(incoming["replayUnchanged"].asBoolean())
            assertTrue(incoming["originalJournalUnchanged"].asBoolean())
            assertTrue(reopened["sameRevision"].asBoolean())
            assertTrue(reopened["runtimeNeverOpened"].asBoolean())
            assertTrue(reopened["databaseReleased"].asBoolean())
            assertNotEquals(adopted["processId"].asInt(), reopened["processId"].asInt())
            assertEquals(incoming["retained"], reopened["incoming"])
            assertEquals(
                events["original"].asString(),
                incoming["retained"]["originalEventId"].asString(),
            )
            assertEquals(
                events["latestEdit"].asString(),
                incoming["retained"]["replacementEventId"].asString(),
            )
            assertEquals(
                events["peer"].asString(),
                incoming["retained"]["redactedEventId"].asString(),
            )
            assertEquals(
                staged["originalHash"].asString(),
                incoming["retained"]["originalHash"].asString(),
            )
            assertEquals(
                staged["latestEditHash"].asString(),
                incoming["retained"]["replacementHash"].asString(),
            )
            val pages = incoming["providerPages"].asInt()
            assertTrue(pages in 1..4)
            assertEquals(pages, matrixTrace.snapshot()["providerPage"])
            assertEquals(30 + pages, matrixTrace.snapshot()["requests"])
            val retained = incoming["retained"]
            assertTrue(retained["physicalProviderPreserved"].asBoolean())
            assertTrue(retained["providerExhaustedWithPending"].asBoolean())
            assertTrue(retained["providerReferences"].asInt() in 5..50)
            assertEquals(
                retained["providerReferences"].asInt() - 2,
                retained["pendingReferences"].asInt(),
            )
            assertEquals(2 + pages, trace.snapshot()["directTransport"])
            assertEquals("Success", staged["companyWindow"]["kind"].asString())
            assertEquals("Success", adopted["spaceWindow"]["kind"].asString())
            assertTrue(failureTrace.snapshot().isEmpty())
            assertEquals(events["latestEdit"].asString(), adopted["headEventId"].asString())
            assertEquals(staged["originalHash"].asString(), adopted["originalHash"].asString())
            assertEquals(staged["latestEditHash"].asString(), adopted["latestEditHash"].asString())
            assertEquals(
                senderEvents,
                provider.encryptedRoomEvents(room, sender.session.getValue("userId")),
            )
            assertEquals(
                recipientEvents,
                provider.encryptedRoomEvents(room, recipient.session.getValue("userId")),
            )
            write(
                directory,
                "accepted",
                mapOf(
                    "encryptedEvents" to 4,
                    "physical" to staged,
                    "adopted" to adopted,
                    "reopened" to reopened,
                    "offlineTransportRemoved" to true,
                    "native" to trace.snapshot(),
                    "matrix" to matrixTrace.snapshot(),
                    "failures" to failureTrace.snapshot(),
                ),
            )
        } finally {
            write(
                directory,
                "request-trace",
                mapOf(
                    "native" to trace.snapshot(),
                    "matrix" to matrixTrace.snapshot(),
                    "failures" to failureTrace.snapshot(),
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
