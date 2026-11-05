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
        ]
)
class NativeCollaborationSdkInteropTest : NativeGatewaySdkFixture() {
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
                                NativeCollaborationSdkInteropTest::class
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
    fun actualAndroidSdkRenewsAccessAndExchangesEncryptedMessagesThroughCurrentHrisGatewayAdmission() {
        val owner = native()
        val sender = sdkOwner(owner)
        val recipient = sdkOwner(sdkPeer(owner))
        val conversation = UUID.randomUUID()
        val at = clock.instant()
        val providerLogin =
            matrix.login(
                CollaborationMatrixLogin(
                    owner.fixture.company,
                    owner.fixture.account,
                    sender.device,
                    at,
                    at.plusSeconds(60),
                )
            )
        assertInstanceOf(Result.Success::class.java, providerLogin)
        val created =
            rooms.create(
                CollaborationMatrixDirectRoom(
                    owner.fixture.company,
                    conversation,
                    owner.fixture.account,
                    recipient.native.fixture.account,
                ),
                (providerLogin as Result.Success).value.accessToken,
                clock.instant().plusSeconds(20),
            )
        assertInstanceOf(Result.Success::class.java, created)
        val room =
            ((created as Result.Success).value as CollaborationMatrixRoomCreation.Created).roomId
        publishSdkRoom(owner, sender.device, recipient.native.fixture.account, room, conversation)
        val heldRecipient = sdkOwner(sdkPeer(owner))
        val heldConversation = UUID.randomUUID()
        val heldCreated =
            rooms.create(
                CollaborationMatrixDirectRoom(
                    owner.fixture.company,
                    heldConversation,
                    owner.fixture.account,
                    heldRecipient.native.fixture.account,
                ),
                providerLogin.value.accessToken,
                clock.instant().plusSeconds(20),
            )
        assertInstanceOf(Result.Success::class.java, heldCreated)
        val heldRoom =
            ((heldCreated as Result.Success).value as CollaborationMatrixRoomCreation.Created)
                .roomId
        publishSdkRoom(
            owner,
            sender.device,
            heldRecipient.native.fixture.account,
            heldRoom,
            heldConversation,
        )
        trace.holdRoom(heldRoom)
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_hris_native.py")) }
        val parent = Files.createDirectories(root.resolve(".work/collaboration"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString().replace("-", "")),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val fixture = directory.resolve("fixture.json")
        writePrivate(
            fixture,
            mapOf(
                "baseUrl" to "http://127.0.0.1:$port",
                // This disposable API credential stays in private fixture input, never proof
                // artifacts.
                "sender" to (sender.session + ("hrisAccessToken" to owner.access)),
                "recipient" to recipient.session,
                "roomId" to room,
                "conversationId" to conversation.toString(),
                "heldRoomId" to heldRoom,
                "heldLogicalId" to UUID.randomUUID().toString(),
                "heldBody" to "fictional-hris-held-${UUID.randomUUID()}",
                "body" to "fictional-hris-${UUID.randomUUID()}",
                "reply" to "fictional-hris-${UUID.randomUUID()}",
                "logicalId" to UUID.randomUUID().toString(),
                "replyId" to UUID.randomUUID().toString(),
                "ackLogicalId" to UUID.randomUUID().toString(),
                "ackBody" to "fictional-hris-ack **literal** <tag> 😀\n${UUID.randomUUID()}",
            ),
        )
        var process: Process? = null
        try {
            val log =
                Files.createFile(
                    directory.resolve("driver.log"),
                    PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")
                    ),
                )
            process =
                ProcessBuilder(
                        "python3",
                        root.resolve("tool/collaboration_hris_native.py").toString(),
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
            val readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(310)
            while (true) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (
                    trace.snapshot().any {
                        it["route"] == "/_matrix/client/v3/keys/upload" && it["status"] == 400
                    }
                )
                    fail<Nothing>(
                        "Owned SDK key upload was rejected; inspect bounded key-shape evidence"
                    )
                val keys =
                    database()
                        .queryForObject(
                            "select count(*) from collaboration_device_key_publications where company_id=? and device_id in (?,?) and version=1",
                            Int::class.java,
                            owner.fixture.company,
                            sender.device,
                            recipient.device,
                        )
                if (Files.exists(directory.resolve("native-ready.json")) && keys == 2) break
                if (System.nanoTime() >= readyDeadline)
                    fail<Nothing>("Owned SDK readiness exceeded its deadline")
                Thread.sleep(100)
            }
            writePrivate(directory.resolve("send.json"), mapOf("ready" to true))
            val restoredDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (!Files.exists(directory.resolve("native-restored.json"))) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (System.nanoTime() >= restoredDeadline)
                    fail<Nothing>("Owned SDK queue restoration exceeded its deadline")
                Thread.sleep(100)
            }
            assertEquals(
                0,
                trace.earlyEncryptedSendCount(),
                "SDK sent before explicit queue admission",
            )
            val renewingOwners = listOf(sender, recipient)
            for (sdk in renewingOwners) assertEquals(
                sdk.session.getValue("gatewayVersion").toLong(),
                gatewayVersion(sdk),
            )
            // Inject server-side credential age after restoration of the original SDK stores.
            // Android wall time stays unchanged; the fixed server clock also keeps the existing
            // bounded refresh replay available for the independent ciphertext verifier below.
            clock.set(
                renewingOwners
                    .maxOf { java.time.Instant.parse(it.session.getValue("accessExpiresAt")) }
                    .plusSeconds(1)
            )
            val renewalDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            while (
                renewingOwners.any {
                    gatewayVersion(it) != it.session.getValue("gatewayVersion").toLong() + 1
                } ||
                    trace.snapshot().count {
                        it["method"] == "POST" &&
                            it["route"] == "/_matrix/client/v3/refresh" &&
                            it["status"] == 200
                    } < 2
            ) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (System.nanoTime() >= renewalDeadline)
                    fail<Nothing>("Original SDK access renewal exceeded its deadline")
                Thread.sleep(100)
            }
            assertEquals(
                2,
                trace.snapshot().count {
                    it["method"] == "POST" &&
                        it["route"] == "/_matrix/client/v3/refresh" &&
                        it["status"] == 200
                },
                "Both original SDK owners must refresh through the HRIS gateway",
            )
            trace.interruptNextRetainedSync()
            val interruptionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!trace.syncInterrupted()) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (System.nanoTime() >= interruptionDeadline)
                    fail<Nothing>("Restored SDK did not issue a retained sync request")
                Thread.sleep(100)
            }
            trace.allowEncryptedSends()
            writePrivate(directory.resolve("resume.json"), mapOf("ready" to true))
            assertTrue(
                process.waitFor(180, TimeUnit.SECONDS),
                "Owned native driver exceeded its deadline",
            )
            assertEquals(0, process.exitValue(), diagnostic(directory))
            val result = json.readTree(Files.readString(directory.resolve("result.json")))
            assertTrue(result.path("passed").asBoolean())
            assertTrue(result.path("cleanup").asBoolean())
            assertTrue(
                trace.retainedSyncRecovered(),
                "SDK failed to retry with its retained cursor",
            )
            val proof = result.path("proof")
            for ((type, minimum) in mapOf("m.read" to 1, "m.read.private" to 4)) {
                assertTrue(
                    trace.snapshot().count {
                        it["method"] == "POST" &&
                            it["status"] == 200 &&
                            it["route"] == "/_matrix/client/v3/rooms/_/receipt/$type/_"
                    } >= minimum,
                    "Native receipt must cross the owned HRIS gateway",
                )
            }
            for (name in
                listOf(
                    "protectedStoreReopened",
                    "missingRecordPreserved",
                    "wrongPassphrasePreserved",
                    "exclusiveOwner",
                    "processRestarted",
                    "queueRestored",
                    "transactionReused",
                    "identityAfterRestart",
                    "messageIntentRestored",
                    "queueMappingRecovered",
                    "queueWriterExclusive",
                    "perRoomDeliveryIsolated",
                    "heldQueueRestored",
                    "nativeDeliveryOwned",
                    "commonSenderOwned",
                    "receiptJournalRecovered",
                    "projectionInterrupted",
                    "originalHistoryWindowOwned",
                    "historyBodiesRestored",
                    "nativeReadReceiptsExchanged",
                    "nativePrivateReceiptIsolated",
                    "nativeReadReceiptStoreRestored",
                    "nativeLocalReadIntentPrepared",
                    "nativeLocalReadIntentStoreRestored",
                    "nativeReadReceiptReplayAdmitted",
                    "nativeReadReceiptAckUncertaintyRecovered",
                    "commonHistoryAcquisitionOwned",
                    "latestHistoryHeadOwned",
                    "providerHistoryPageOwned",
                    "commonProviderHistoryAcquisitionOwned",
                    "providerHistoryReferencesRestored",
                    "providerHistoryReferencesReconciled",
                    "providerHistoryRestartPreserved",
                    "historyRuntimeReopened",
                    "ackBeforeJournalRecovered",
                    "processRestartedAfterAck",
                    "completionCleanupRecovered",
                    "coldHistoryRecoveredOffline",
                    "gatewayUnavailableAfterProcessDeath",
                    "processRestartedAfterCompletion",
                )) {
                assertTrue(proof.path(name).isBoolean && proof.path(name).asBoolean())
            }
            assertEquals(
                0,
                trace.earlyEncryptedSendCount(),
                "An early send completed after queue admission",
            )
            assertEquals(
                0,
                trace.heldRoomSendCount(),
                "Unadmitted room attempted an encrypted send",
            )
            // No fixture refresh is sent until native exchange, restarts and assertions finish.
            // These requests replay the original successful SDK refresh; they may not rotate again.
            val renewedSender = replaySdkRefresh(sender)
            val renewedRecipient = replaySdkRefresh(recipient)
            verifyCiphertext(
                renewedSender,
                room,
                proof.path("senderEventId").asString(),
                sender.session.getValue("userId"),
            )
            verifyCiphertext(
                renewedRecipient,
                room,
                proof.path("recipientEventId").asString(),
                recipient.session.getValue("userId"),
            )
            verifyCiphertext(
                renewedSender,
                room,
                proof.path("ackSenderEventId").asString(),
                sender.session.getValue("userId"),
            )
            assertEquals(
                3,
                trace.snapshot().count {
                    it["method"] == "PUT" &&
                        it["route"] == "/_matrix/client/v3/rooms/_/send/m.room.encrypted/_" &&
                        it["status"] == 200
                },
                "Original, reply and acknowledged message must each send once",
            )
            val applicationReads = trace.applicationAdmissions()
            assertTrue(
                listOf("device", "conversation", "room").all { applicationReads.getValue(it) >= 10 }
            )
            assertEquals(0, applicationReads.getValue("failures"))
            assertTrue(trace.applicationAdmittedBeforeSend())
            assertTrue(trace.applicationAdmittedAroundHistory())
            assertTrue(trace.admittedHistoryHeads())
            assertTrue(trace.admittedProviderPages())
            val history = proof.path("historyWindow")
            for ((name, bounds) in
                mapOf(
                    "originals" to 2L..2L,
                    "unavailable" to 0L..128L,
                    "extractionNanos" to 0L..5_000_000_000L,
                    "modelCharacters" to 1L..4_194_304L,
                    "originalCharacters" to 1L..4_194_304L,
                )) {
                val field = history.path(name)
                assertTrue(
                    field.isIntegralNumber && field.canConvertToLong() && field.asLong() in bounds
                )
            }
            writePrivate(
                directory.resolve("verified.json"),
                mapOf(
                    "passed" to true,
                    "encryptedBothDirections" to true,
                    "sdkAccessRenewed" to true,
                    "sameGatewayAfterRenewal" to true,
                    "verificationUsedRefreshReplay" to true,
                    "logicalIdsPreserved" to true,
                    "cleanup" to true,
                    "protectedStoreReopened" to true,
                    "missingRecordPreserved" to true,
                    "wrongPassphrasePreserved" to true,
                    "exclusiveOwner" to true,
                    "processRestarted" to true,
                    "queueRestored" to true,
                    "transactionReused" to true,
                    "identityAfterRestart" to true,
                    "messageIntentRestored" to true,
                    "queueMappingRecovered" to true,
                    "queueWriterExclusive" to true,
                    "perRoomDeliveryIsolated" to true,
                    "heldQueueRestored" to true,
                    "nativeDeliveryOwned" to true,
                    "commonSenderOwned" to true,
                    "receiptJournalRecovered" to true,
                    "projectionInterrupted" to true,
                    "originalHistoryWindowOwned" to true,
                    "historyBodiesRestored" to true,
                    "nativeReadReceiptsExchanged" to true,
                    "nativePrivateReceiptIsolated" to true,
                    "nativeReadReceiptStoreRestored" to true,
                    "nativeLocalReadIntentPrepared" to true,
                    "nativeLocalReadIntentStoreRestored" to true,
                    "nativeReadReceiptReplayAdmitted" to true,
                    "nativeReadReceiptAckUncertaintyRecovered" to true,
                    "commonHistoryAcquisitionOwned" to true,
                    "latestHistoryHeadOwned" to true,
                    "admittedHistoryHeads" to true,
                    "providerHistoryPageOwned" to true,
                    "commonProviderHistoryAcquisitionOwned" to true,
                    "providerHistoryReferencesRestored" to true,
                    "providerHistoryReferencesReconciled" to true,
                    "providerHistoryRestartPreserved" to true,
                    "historyRuntimeReopened" to true,
                    "ackBeforeJournalRecovered" to true,
                    "processRestartedAfterAck" to true,
                    "completionCleanupRecovered" to true,
                    "coldHistoryRecoveredOffline" to true,
                    "gatewayUnavailableAfterProcessDeath" to true,
                    "processRestartedAfterCompletion" to true,
                    "encryptedSendCount" to 3,
                    "admittedProviderPages" to true,
                    "historyWindow" to history,
                    "applicationAdmittedBeforeSend" to true,
                    "applicationAdmittedAroundHistory" to true,
                    "applicationAdmissions" to trace.applicationAdmissions(),
                    "heldRoomNeverSent" to true,
                    "pausedAcrossRestart" to true,
                    "syncInterruptedAfterRestart" to true,
                    "syncRetriedWithRetainedCursor" to true,
                    "artifact" to result.path("artifact"),
                ),
            )
        } finally {
            val interrupted = Thread.interrupted()
            try {
                if (process?.isAlive == true) {
                    process.destroy()
                    if (!process.waitFor(90, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        process.waitFor(5, TimeUnit.SECONDS)
                    }
                }
            } finally {
                try {
                    writePrivate(directory.resolve("gateway-trace.json"), trace.snapshot())
                } finally {
                    Files.deleteIfExists(fixture)
                    clock.followSystemTime()
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun gatewayVersion(owner: SdkOwner): Long =
        requireNotNull(
            database()
                .queryForObject(
                    "select version from collaboration_gateway_sessions where id=? and native_session_id=? and device_id=? and revoked_at is null",
                    Long::class.java,
                    UUID.fromString(owner.session.getValue("gatewaySessionId")),
                    owner.native.session,
                    owner.device,
                )
        )

    private fun replaySdkRefresh(owner: SdkOwner): SdkOwner {
        val expected = owner.session.getValue("gatewayVersion").toLong() + 1
        assertEquals(expected, gatewayVersion(owner))
        val response =
            owner.native.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/refresh"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(
                                mapOf("refresh_token" to owner.session.getValue("refreshToken"))
                            )
                        )
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode(), "Original SDK refresh replay failed")
        val value = json.readTree(response.body())
        val access = value.path("access_token").asString()
        assertTrue(access.isNotBlank() && access != owner.session.getValue("accessToken"))
        assertEquals(expected, gatewayVersion(owner), "Verification replay rotated a gateway")
        return SdkOwner(owner.native, owner.device, owner.session + ("accessToken" to access))
    }

    private fun verifyCiphertext(owner: SdkOwner, room: String, event: String, sender: String) {
        fun encode(value: String) =
            java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
        val response =
            owner.native.fixture.browser.send(
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
        assertEquals(sender, value.path("sender").asString())
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
        if (!Files.isRegularFile(path)) return "Owned native driver exited without evidence"
        val result = json.readTree(Files.readString(path))
        return "Owned native gateway failed: " +
            result.path("failureCategory").asString() +
            " " +
            result.path("diagnostic").toString()
    }
}
