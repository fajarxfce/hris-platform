package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceReadinessChannel
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationSpaceReadiness
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
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
import tools.jackson.databind.JsonNode

@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_RECOVERY_APK", matches = ".+")
@Import(
    NativeOriginalDeliveryTraceConfiguration::class,
    NativeSpaceRecoveryTraceConfiguration::class,
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
class NativeSpaceRecoveryWorkerTest : NativeSpaceOriginalFixture() {
    @Autowired private lateinit var trace: NativeOriginalDeliveryTrace
    @Autowired private lateinit var matrixTrace: NativeSpaceRecoveryTrace
    @Autowired private lateinit var readiness: FanOutCollaborationSpaceReadiness

    companion object {
        private val provider by lazy { NativeSdkMatrixServer(loginBurst = 32) }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeSpaceRecoveryWorkerTest::class
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
    fun actualReadinessWakesRegisteredGroupTaskAndRecoversClaimAfterProcessLoss() {
        val base = native()
        val sender = sdkOwner(sdkPeer(base))
        val recipient = sdkOwner(sdkPeer(base))
        val conversation = UUID.randomUUID()
        val room = originalRoom(sender, recipient, conversation)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_space_recovery.py")) }
        val parent = Files.createDirectories(root.resolve(".work/collaboration-space-recovery"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val ids = List(2) { UUID.randomUUID().toString() }
        write(
            directory,
            "fixture",
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                "roomId" to room,
                "conversationId" to conversation,
                "logicalId" to ids[0],
                "secondLogicalId" to ids[1],
                "body" to "fictional-hris-worker-original-${UUID.randomUUID()}",
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
                    root.resolve("tool/collaboration_space_recovery.py").toString(),
                    "--apk",
                    requireNotNull(System.getenv("HRIS_COLLAB_RECOVERY_APK")),
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
            write(directory, "stage-allowed", mapOf("ready" to true))
            val staged = waitFor(directory, "staged", process)
            assertEquals(ready["sender"], staged["sender"])
            assertEquals(ready["recipient"], staged["recipient"])
            assertTrue(staged["bothSdkClosed"].asBoolean())
            assertTrue(staged["recipientClosedBeforeDelivery"].asBoolean())
            assertEquals(
                emptyList<String>(),
                provider.encryptedRoomEvents(room, sender.session.getValue("userId")),
            )
            val group = completeOriginal(sender, ready["sender"]["fingerprint"].asString())
            val peerGroup = prepareOriginal(recipient, ready["recipient"]["fingerprint"].asString())
            assertEquals(group.spaceId, peerGroup.spaceId)
            val canonical = adoptRoom(sender, recipient, group, room, recipientReady = false)
            trace.arm()
            matrixTrace.arm()
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
            val listening = waitFor(directory, "listening", process)
            assertTrue(listening["nativeSyncActive"].asBoolean())
            assertTrue(listening["startupWakeCleared"].asBoolean())
            assertEquals(
                emptyList<String>(),
                provider.encryptedRoomEvents(room, sender.session.getValue("userId")),
            )
            // The first attempt created the room after both publications, correctly selecting
            // zero targets. Publish this recipient only after the retained conversation exists.
            completeOriginal(recipient, ready["recipient"]["fingerprint"].asString(), peerGroup)
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_readiness_deliveries d join collaboration_space_room_bindings b on b.space_id=d.space_id where d.publisher_device_id=? and d.channel='MATRIX' and b.conversation_id=? and b.created_at<=d.captured_at",
                        Int::class.java,
                        peerGroup.deviceId,
                        canonical,
                    ),
            )
            withWorker {
                val leases =
                    success(
                        readiness.claim(
                            CollaborationSpaceReadinessChannel.MATRIX,
                            UUID.randomUUID(),
                            4,
                        )
                    )
                assertEquals(2, leases.size)
                for (lease in leases) success(readiness.fanOut(lease))
            }
            assertEquals(
                sender.native.fixture.account,
                database()
                    .queryForObject(
                        "select after_account_id from collaboration_space_readiness_deliveries where publisher_device_id=? and channel='MATRIX' and completed_at is not null",
                        UUID::class.java,
                        peerGroup.deviceId,
                    ),
            )
            val control = waitFor(directory, "control", process)
            assertTrue(control["durableWake"].asBoolean())
            assertTrue(control["capturedReplayDeduplicated"].asBoolean())
            val interrupted = waitFor(directory, "ack", process)
            assertTrue(interrupted["claimedBeforeDelivery"].asBoolean())
            assertTrue(interrupted["journalDurable"].asBoolean())
            assertTrue(interrupted["projectionNotWritten"].asBoolean())
            val recovered = waitFor(directory, "recovered", process)
            assertTrue(recovered["groupTaskCompleted"].asBoolean())
            assertTrue(recovered["progressAndWakeCompleted"].asBoolean())
            assertTrue(recovered["idleRecoveryCompleted"].asBoolean())
            assertTrue(recovered["recoveredWithoutNewControl"].asBoolean())
            val actualEvents = provider.encryptedRoomEvents(room, sender.session.getValue("userId"))
            assertOriginalReceipts(ids, staged, recovered, actualEvents)
            val result = waitFor(directory, "result", process)
            assertTrue(process.waitFor(20, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            assertTrue(result["passed"].asBoolean())
            assertTrue(result["cleanup"].asBoolean())
            val requests = trace.snapshot()
            val sdkRequests = matrixTrace.snapshot()
            assertTrue(sdkRequests.getOrDefault("requests", 0) in 1..80)
            assertEquals(0, sdkRequests.getOrDefault("rejected", 0))
            assertEquals(0, sdkRequests.getOrDefault("serverError", 0))
            // All native lifetimes and driver children have terminated before this quiet check.
            Thread.sleep(1_000)
            assertEquals(sdkRequests, matrixTrace.snapshot())
            assertEquals(requests, trace.snapshot())
            // Interrupted original, then recovered original; an explicit fresh idle sweep
            // must complete locally without another direct transport request.
            assertEquals(2, requests.getOrDefault("directTransport", 0))
            assertEquals(1, requests["bootstrapReplay"])
            assertEquals(1, requests["registration"])
            assertTrue(requests.getOrDefault("requests", 0) in 8..64)
            for (kind in listOf("roomRequest", "failed")) assertEquals(
                0,
                requests.getOrDefault(kind, 0),
            )
            write(
                directory,
                "accepted",
                mapOf(
                    "actualProviderEvents" to actualEvents.size,
                    "confirmedOriginals" to ids.size,
                    "recipientOffline" to true,
                    "originalTransactionsRetained" to true,
                    "interruptedBeforeProjection" to true,
                    "localReadBeforeTransport" to true,
                    "driver" to result,
                    "nativeRequests" to requests,
                    "matrixRequests" to sdkRequests,
                    "actualGroupControl" to true,
                    "consumerReplayDeduplicated" to true,
                    "claimedBeforeDelivery" to true,
                    "idleRecoveryCompleted" to true,
                    "idleSweepAdditionalDirectTransport" to 0,
                    "recoveredWithoutFcm" to true,
                    "groupKeyPublication" to
                        "Actual original worker publishes verified signed identity with transfer. One exact fixture registration/bootstrap replay seeds encrypted local setup; recovery reuses it. No fixture key upload or key-confirmation endpoint.",
                ),
            )
        } finally {
            // Retain safe transport counts even when a native checkpoint fails to arrive.
            write(
                directory,
                "traffic",
                mapOf("native" to trace.snapshot(), "matrix" to matrixTrace.snapshot()),
            )
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                }
            }
            // Remove only the ephemeral HTTP credential file; retained proof app data belongs to
            // the driver and survives failed experiments. Never touch employee stores.
            Files.deleteIfExists(directory.resolve("fixture.json"))
        }
    }

    private fun assertOriginalReceipts(
        ids: List<String>,
        staged: JsonNode,
        recovered: JsonNode,
        actual: List<String>,
    ) {
        assertTrue(recovered["originalsRetained"].asBoolean())
        assertTrue(recovered["recipientStayedOffline"].asBoolean())
        val original =
            staged["originals"].associate {
                it["logicalId"].asString() to it["transactionId"].asString()
            }
        val confirmed =
            recovered["confirmed"].associate {
                it["logicalId"].asString() to it["transactionId"].asString()
            }
        assertEquals(ids.toSet(), original.keys)
        assertEquals(2, original.values.toSet().size)
        assertEquals(original, confirmed)
        val receipts =
            recovered["confirmed"].flatMap { record ->
                record["events"].iterator().asSequence().map { it.asString() }.toList()
            }
        assertEquals(2, actual.size)
        assertEquals(actual.toSet(), receipts.toSet())
        assertTrue(recovered["confirmed"].all { it["events"].size() == 1 })
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
                fail<Nothing>("Original delivery phase $name failed: $category")
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
