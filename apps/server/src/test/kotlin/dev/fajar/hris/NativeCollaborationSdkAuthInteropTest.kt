package dev.fajar.hris

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
class NativeCollaborationSdkAuthInteropTest : NativeGatewaySdkFixture() {
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
                                NativeCollaborationSdkAuthInteropTest::class
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
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun actualSdkRecoversRejectedRefreshOnTheOriginalGatewayWithoutReplacingPrivateKeys() {
        val owner = sdkOwner(native())
        val root =
            generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
                .take(5)
                .first { Files.isRegularFile(it.resolve("tool/collaboration_sdk_auth_native.py")) }
        val parent = Files.createDirectories(root.resolve(".work/collaboration"))
        val directory =
            Files.createDirectory(
                parent.resolve(UUID.randomUUID().toString().replace("-", "")),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val fixture = directory.resolve("fixture.json")
        writePrivate(
            fixture,
            mapOf("baseUrl" to "http://127.0.0.1:$port", "owner" to owner.session),
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
                        root.resolve("tool/collaboration_sdk_auth_native.py").toString(),
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
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (
                !Files.exists(directory.resolve("native-ready.json")) || publications(owner) != 1
            ) {
                if (!process.isAlive) fail<Nothing>(diagnostic(directory))
                if (System.nanoTime() >= deadline)
                    fail<Nothing>("Owned auth fixture did not become ready")
                Thread.sleep(100)
            }
            val originalVersion = owner.session.getValue("gatewayVersion").toLong()
            assertEquals(originalVersion, gatewayVersion(owner))
            // The fixture clock is fixed; move beyond the real ten-second rotation guard.
            clock.set(clock.instant().plusSeconds(11))
            // A second admitted bootstrap supersedes SDK credentials on the SAME device and
            // gateway. Native HRIS access remains active: this is not an account revocation test.
            val replacement = rotateOriginalGateway(owner, originalVersion)
            writePrivate(directory.resolve("replacement.json"), replacement)
            assertEquals(originalVersion + 1, gatewayVersion(owner))
            assertTrue(
                process.waitFor(100, TimeUnit.SECONDS),
                "Owned auth probe exceeded its deadline",
            )
            assertEquals(0, process.exitValue(), diagnostic(directory))
            val result = json.readTree(Files.readString(directory.resolve("result.json")))
            val flags =
                listOf(
                    "passed",
                    "cleanup",
                    "typedAuthRejection",
                    "liveSyncRejected",
                    "runtimeEntryRejected",
                    "localReadPolicyPreserved",
                    "sameStoreCredentialsReplaced",
                    "actualSdkSyncRestored",
                    "originalKeysPreserved",
                    "ownedRuntimeDrained",
                )
            for (flag in flags) assertTrue(result.path(flag).asBoolean(), flag)
            assertTrue(
                trace.snapshot().any {
                    it["route"] == "/_matrix/client/v3/refresh" && it["status"] == 401
                },
                "Actual SDK refresh was not rejected through HRIS",
            )
            assertEquals(1, publications(owner))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_devices where id=? and state='ACTIVE' and version=1",
                        Int::class.java,
                        owner.device,
                    ),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from native_sessions where id=? and revoked_at is null",
                        Int::class.java,
                        owner.native.session,
                    ),
            )
            assertEquals(originalVersion + 1, gatewayVersion(owner))
            writePrivate(
                directory.resolve("verified.json"),
                mapOf(
                    "actualSdkAuthRejected" to true,
                    "actualSdkAuthRecovered" to true,
                    "sameGatewayRotatedOnce" to true,
                    "hrisParentStillActive" to true,
                    "originalDeviceStillActive" to true,
                    "oneKeyPublication" to true,
                    "native" to result,
                    "gateway" to trace.snapshot(),
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
                    clearFixture(fixture, directory.resolve("replacement.json"))
                } finally {
                    clock.followSystemTime()
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun clearFixture(original: Path, replacement: Path) {
        try {
            Files.deleteIfExists(original)
        } finally {
            Files.deleteIfExists(replacement)
        }
    }

    private fun rotateOriginalGateway(owner: SdkOwner, version: Long): Map<String, String> {
        val operation = UUID.randomUUID()
        val response =
            owner.native.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port${owner.native.path}/bootstrap"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${owner.native.access}")
                    .header("Content-Type", "application/json")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
                    .header("Idempotency-Key", operation.toString())
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(
                                mapOf(
                                    "deviceId" to owner.device,
                                    "expectedDeviceVersion" to 1,
                                    "expectedGatewayVersion" to version,
                                )
                            )
                        )
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode(), "Original gateway bootstrap failed")
        val value = json.readTree(response.body())
        assertEquals(owner.session.getValue("gatewaySessionId"), value.path("sessionId").asString())
        assertEquals(owner.session.getValue("userId"), value.path("matrixUserId").asString())
        assertEquals(owner.session.getValue("deviceId"), value.path("matrixDeviceId").asString())
        assertEquals(version + 1, value.path("version").asLong())
        return owner.session +
            mapOf(
                "bootstrapOperationId" to operation.toString(),
                "gatewayVersion" to value.path("version").asLong().toString(),
                "accessToken" to value.path("accessToken").asString(),
                "refreshToken" to value.path("refreshToken").asString(),
                "accessExpiresAt" to value.path("accessExpiresAt").asString(),
                "expiresAt" to value.path("expiresAt").asString(),
            )
    }

    private fun publications(owner: SdkOwner): Int =
        requireNotNull(
            database()
                .queryForObject(
                    "select count(*) from collaboration_device_key_publications where company_id=? and device_id=? and version=1",
                    Int::class.java,
                    owner.native.fixture.company,
                    owner.device,
                )
        )

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
        if (!Files.isRegularFile(path)) return "Owned auth driver exited without evidence"
        val result = json.readTree(Files.readString(path))
        return "Owned auth fixture failed: " +
            result.path("failureCategory").asString() +
            " " +
            result.path("diagnostic").toString()
    }
}
