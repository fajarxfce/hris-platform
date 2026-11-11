package dev.fajar.hris

import dev.fajar.hris.collaboration.data.repositories.DisabledCollaborationSpaceMatrixRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRepository
import dev.fajar.hris.collaboration.domain.usecases.ClaimCollaborationSpaceProvisioning
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class NativeCollaborationSpaceDisabledConfigurationTest : NativeCollaborationApiFixture() {
    @Autowired private lateinit var policy: CollaborationSpacePolicy
    @Autowired private lateinit var matrix: CollaborationSpaceMatrixRepository
    @Autowired private lateinit var claim: ClaimCollaborationSpaceProvisioning

    @Test
    fun defaultApiGraphKeepsGroupProviderAndClaimsDisabled() {
        assertFalse(policy.enabled)
        assertInstanceOf(DisabledCollaborationSpaceMatrixRepository::class.java, matrix)
        assertEquals(
            Result.Success(emptyList<CollaborationSpaceProvisioningLease>()),
            claim.execute(UUID.randomUUID(), 1),
        )
    }

    @Test
    fun disabledGroupHttpCannotDiscoverContactsSelectOrRegister() {
        val n = native()
        val root = "/api/v1/collaboration/spaces"
        val base = "$root/${UUID.randomUUID()}"
        val fenceOperation = UUID.randomUUID()
        val paths =
            listOf(
                "$root/current" to null,
                "$base/contacts" to null,
                "$base/direct-conversations" to null,
                "$base/direct-conversations/${UUID.randomUUID()}" to null,
                "$base/direct-conversations" to "{\"peerAccountId\":\"${UUID.randomUUID()}\"}",
                "$base/direct-conversations/${UUID.randomUUID()}/room/requests" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0}""",
                "$base/direct-conversations/${UUID.randomUUID()}/room/requests/fence" to
                    """{"operationId":"$fenceOperation","deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0}""",
                "$base/direct-conversations/${UUID.randomUUID()}/room/requests/receipts/read" to
                    """{"operationId":"${UUID.randomUUID()}","deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0}""",
                "$base/direct-conversations/${UUID.randomUUID()}/room/receipts/read" to
                    """{"operationId":"${UUID.randomUUID()}","nativeSessionId":"${UUID.randomUUID()}","nativeSessionVersion":0,"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0,"platform":"ANDROID","clientBuild":28}""",
                "$base/legacy-conversations" to null,
                "$base/legacy-rooms/read" to
                    """{"companyId":"${UUID.randomUUID()}","matrixRoomId":"!original:matrix.example.test"}""",
                "$base/legacy-conversations/${UUID.randomUUID()}/${UUID.randomUUID()}" to null,
                "$base/native-device" to null,
                "$base/identity" to "{}",
                "$base/native-device" to
                    """{"platform":"ANDROID","label":"Fictional disabled","origin":"SPACE"}""",
                "$base/native-device/bootstrap" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0}""",
                "$base/native-device/provider/renew" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0}""",
                "$base/native-device/retry" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0,"expectedProvisioningRevision":0}""",
                "$base/native-device/legacy-runtime/publication" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0,"fingerprint":"${"a".repeat(64)}"}""",
                "$base/native-device/legacy-runtime/freeze" to
                    """{"deviceId":"${UUID.randomUUID()}","expectedDeviceVersion":0,"fingerprint":"${"a".repeat(64)}"}""",
                "$base/native-device/legacy-runtime/drain?deviceId=${UUID.randomUUID()}&expectedDeviceVersion=0&fingerprint=${"a".repeat(64)}" to
                    null,
            )
        for ((path, body) in paths) {
            val request =
                java.net.http.HttpRequest.newBuilder(java.net.URI("http://127.0.0.1:$port$path"))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "28")
            if (body == null) request.GET()
            else
                request
                    .header("Content-Type", "application/json")
                    .header(
                        "Idempotency-Key",
                        (if (path.endsWith("/requests/fence")) fenceOperation
                            else UUID.randomUUID())
                            .toString(),
                    )
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            failure(
                n.fixture.browser.send(
                    request.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString(),
                ),
                503,
                "collaboration_unavailable",
            )
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }
}
