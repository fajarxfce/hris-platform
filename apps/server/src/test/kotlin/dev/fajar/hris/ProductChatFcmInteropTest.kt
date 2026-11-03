package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRoomRepository
import dev.fajar.hris.core.domain.Result
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/** Explicit external-provider opt-in. The employee app and sender fixture use separate packages. */
@EnabledIfEnvironmentVariable(named = "HRIS_COLLAB_PRODUCT_FCM_APK", matches = ".+")
@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_PUSH_ENABLED=true",
            "HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED=true",
            "HRIS_MATRIX_PUSH_CALLBACK_SOURCES=127.0.0.1/32",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
            "HRIS_FIREBASE_AUTH_ENABLED=false",
            "HRIS_FCM_ENABLED=false",
            "HRIS_MAIL_ENABLED=false",
        ]
)
class ProductChatFcmInteropTest : NativeGatewaySdkFixture() {
    companion object {
        private val provider by lazy { MatrixPushProviderFixture() }

        @JvmStatic
        @DynamicPropertySource
        fun matrixProperties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin.toString() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") { provider.signingFile.toString() }
            registry.add("HRIS_MATRIX_PUSH_GATEWAY_URL") { provider.callback }
        }

        @JvmStatic @AfterAll fun closeProvider() = provider.close()
    }

    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var rooms: CollaborationMatrixRoomRepository

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    fun realFcmStartsProductAndEncryptedInlineReplyReturnsToThePeer() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        provider.startProxy(port)
        val owner = native()
        val control = sdkOwner(owner)
        val peer = sdkOwner(sdkPeer(owner))
        val conversation = UUID.randomUUID()
        val login =
            matrix.login(
                CollaborationMatrixLogin(
                    owner.fixture.company,
                    owner.fixture.account,
                    control.device,
                    clock.instant(),
                    clock.instant().plusSeconds(60),
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
        publishSdkRoom(owner, control.device, peer.native.fixture.account, room, conversation)
        // sdkOwner seeds ACTIVE devices directly. Retire only those two seeded provisioning
        // requests before starting the real worker; product UI enrollment remains real work.
        assertEquals(
            2,
            database()
                .update(
                    """update background_jobs set status='CANCELLED',finished_at=now(),version=version+1
                    where company_id=? and kind='COLLABORATION_PROVISION' and status='QUEUED'
                    and request->>'deviceId' in (?,?)""",
                    owner.fixture.company,
                    control.device.toString(),
                    peer.device.toString(),
                ),
        )
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_product_fcm.py")) }
        val parent = Files.createDirectories(root.resolve(".work/product-chat-fcm"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString()),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val fixture = directory.resolve("fixture.json")
        Files.writeString(
            fixture,
            json.writeValueAsString(
                mapOf(
                    "baseUrl" to "http://127.0.0.1:$port",
                    "email" to "admin@example.test",
                    "password" to "Testing-password-123!",
                    "accountId" to owner.fixture.account.toString(),
                    "companyId" to owner.fixture.company.toString(),
                    "conversationId" to conversation.toString(),
                    "roomId" to room,
                    "peer" to peer.session,
                    "body" to "Fictional FCM ${UUID.randomUUID()}",
                    "reply" to "Fictional reply ${UUID.randomUUID()}",
                    "logicalId" to UUID.randomUUID().toString(),
                )
            ),
        )
        Files.setPosixFilePermissions(fixture, PosixFilePermissions.fromString("rw-------"))
        try {
            ProductChatFcmWorker(root, directory, database(), postgres.jdbcUrl, provider).use {
                worker ->
                runDriver(root, directory, worker, owner)
                val result = json.readTree(Files.readString(directory.resolve("result.json")))
                assertTrue(
                    result.path("passed").asBoolean(),
                    "Owned product FCM driver did not pass",
                )
                assertTrue(result.path("encryptedReplyReceived").asBoolean())
                assertTrue(result.path("productStartedByPush").asBoolean())
                assertTrue(result.path("notificationShown").asBoolean())
                assertTrue(result.path("cleanedUp").asBoolean())
            }
        } finally {
            Files.deleteIfExists(fixture)
        }
    }

    private fun runDriver(
        root: Path,
        directory: Path,
        worker: ProductChatFcmWorker,
        owner: Native,
    ) {
        val log =
            Files.createFile(
                directory.resolve("driver.log"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
        val process =
            ProcessBuilder(
                    "python3",
                    root.resolve("tool/collaboration_product_fcm.py").toString(),
                    "--directory",
                    directory.toString(),
                )
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        try {
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(9)
            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                worker.checkAlive()
                check(System.nanoTime() < deadline) { "Owned product FCM driver deadline exceeded" }
                val counts =
                    database()
                        .queryForMap(
                            """select
                        (select count(*) from native_push_registrations where account_id=? and enabled) as registrations,
                        (select count(*) from collaboration_push_bindings where company_id=? and account_id=? and published_at is not null) as pushers,
                        (select coalesce(sum(delivered_revision),0) from collaboration_push_hints where company_id=? and account_id=?) as delivered""",
                            owner.fixture.account,
                            owner.fixture.company,
                            owner.fixture.account,
                            owner.fixture.company,
                            owner.fixture.account,
                        )
                val temporary = directory.resolve("server-status.next")
                Files.writeString(temporary, json.writeValueAsString(counts))
                Files.move(
                    temporary,
                    directory.resolve("server-status.json"),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
            assertEquals(
                0,
                process.exitValue(),
                "Owned product FCM driver failed; inspect sanitized result",
            )
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    check(process.waitFor(5, TimeUnit.SECONDS))
                }
            }
        }
    }
}
