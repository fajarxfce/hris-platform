package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationSpaceDeviceStatus
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceDeviceStatusPolicyTest {
    private val at = Instant.parse("2026-11-03T10:00:00Z")
    private val device =
        CollaborationSpaceDevice(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "@hrisg_fixture:matrix.example.test",
            "HRISG_fixture",
            CollaborationSpaceDeviceOrigin.SPACE,
            null,
            null,
            UUID.randomUUID(),
            CollaborationPlatform.ANDROID,
            "Fictional",
            CollaborationDeviceState.PROVISIONING,
            0,
            at,
            at,
            null,
        )
    private val request =
        CollaborationSpaceProvisioningRequest(
            device.spaceId,
            device.accountId,
            device.id,
            device.originNativeSessionId,
            0,
            0,
            0,
            at.minusSeconds(30),
            null,
            UUID.randomUUID(),
            ClientVersion(ClientPlatform.ANDROID, 28),
            at,
            at.plusSeconds(60),
        )
    private val queued =
        CollaborationSpaceProvisioning(
            request,
            at,
            at,
            CollaborationSpaceProvisioningState.QUEUED,
            0,
            null,
            null,
        )

    @Test
    fun recoveryMetadataKeepsExpiredOwnedHeadAndRejectsForeignOrImpossibleCoordinates() {
        val active = device.copy(state = CollaborationDeviceState.ACTIVE, version = 1)
        val completed = queued.copy(state = CollaborationSpaceProvisioningState.SUCCEEDED)
        val gateway =
            CollaborationSpaceGatewaySession(
                UUID.randomUUID(),
                device.spaceId,
                device.accountId,
                device.id,
                device.originNativeSessionId,
                device.platform,
                28,
                0,
                at,
                at,
                at.plusSeconds(600),
                at.plusSeconds(300),
            )
        val expired =
            collaborationSpaceDeviceStatus(active, completed, at.plusSeconds(900), gateway)
        assertInstanceOf(Result.Success::class.java, expired)
        val metadata = requireNotNull((expired as Result.Success).value.gateway)
        assertEquals(gateway.id, metadata.id)
        assertEquals(gateway.accessExpiresAt, metadata.accessExpiresAt)
        assertEquals(gateway.expiresAt, metadata.expiresAt)
        val retired = gateway.copy(version = 1000000, revokedAt = at.plusSeconds(20))
        val revoked =
            active.copy(state = CollaborationDeviceState.REVOKED, revokedAt = at.plusSeconds(20))
        assertEquals(
            retired.revokedAt,
            (collaborationSpaceDeviceStatus(revoked, completed, at.plusSeconds(900), retired)
                    as Result.Success)
                .value
                .gateway
                ?.revokedAt,
        )
        for (invalid in
            listOf(
                gateway.copy(spaceId = UUID.randomUUID()),
                gateway.copy(accountId = UUID.randomUUID()),
                gateway.copy(deviceId = UUID.randomUUID()),
                gateway.copy(nativeSessionId = UUID.randomUUID()),
                gateway.copy(platform = CollaborationPlatform.IOS),
                gateway.copy(createdAt = at.minusSeconds(1)),
                gateway.copy(rotatedAt = at.plusSeconds(1)),
                gateway.copy(accessExpiresAt = at),
                gateway.copy(expiresAt = at.plusSeconds(299)),
                gateway.copy(version = -1),
                gateway.copy(clientBuild = 0),
                gateway.copy(revokedAt = at.plusSeconds(1)),
            )) assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceDeviceStatus(active, completed, at, invalid),
        )
        assertNull(
            (collaborationSpaceDeviceStatus(active, completed, at) as Result.Success).value.gateway
        )
    }

    @Test
    fun statusKeepsDeadlineAndPriorOutcomeWhileRetryIsExplicitlyBounded() {
        val first = status(device, queued, at)
        assertEquals(at.plusSeconds(60), first.provisioning.retryNotBefore)
        val running =
            queued.copy(
                state = CollaborationSpaceProvisioningState.RUNNING,
                attempts = 1,
                lease =
                    CollaborationSpaceProvisioningLease(
                        request,
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        request.expiresAt,
                    ),
            )
        assertEquals(1, status(device, running, at.plusSeconds(10)).provisioning.attempts)
        val expired = status(device, running, at.plusSeconds(60))
        assertEquals(CollaborationSpaceProvisioningState.RUNNING, expired.provisioning.state)
        assertEquals(expired.observedAt, expired.provisioning.retryNotBefore)
        for (phase in
            listOf(
                CollaborationSpaceProvisioningState.FAILED,
                CollaborationSpaceProvisioningState.CANCELLED,
            )) {
            val outcome =
                CollaborationSpaceProvisioningOutcome(
                    0,
                    phase,
                    at.plusSeconds(20),
                    if (phase == CollaborationSpaceProvisioningState.FAILED)
                        CollaborationSpaceProvisioningFailure.PROVIDER_UNAVAILABLE
                    else null,
                )
            val ended = running.copy(state = phase, lease = null, lastOutcome = outcome)
            val result = status(device, ended, at.plusSeconds(25))
            assertEquals(outcome, result.provisioning.lastOutcome)
            assertEquals(at.plusSeconds(60), result.provisioning.retryNotBefore)
            val retry =
                ended.copy(
                    request =
                        request.copy(
                            revision = 1,
                            requestedAt = at.plusSeconds(60),
                            expiresAt = at.plusSeconds(120),
                            nativeSessionVersion = 1,
                        ),
                    state = CollaborationSpaceProvisioningState.QUEUED,
                    attempts = 0,
                    availableAt = at.plusSeconds(60),
                )
            val retried = status(device, retry, at.plusSeconds(60))
            assertEquals(1, retried.provisioning.revision)
            assertEquals(outcome, retried.provisioning.lastOutcome)
            assertEquals(at.plusSeconds(120), retried.provisioning.retryNotBefore)
        }
        val longRequest = queued.copy(request = request.copy(expiresAt = at.plusSeconds(300)))
        assertEquals(
            at.plusSeconds(300),
            status(device, longRequest, at).provisioning.retryNotBefore,
        )
        val succeeded =
            running.copy(
                state = CollaborationSpaceProvisioningState.SUCCEEDED,
                lease = null,
                lastOutcome =
                    CollaborationSpaceProvisioningOutcome(
                        0,
                        CollaborationSpaceProvisioningState.SUCCEEDED,
                        at.plusSeconds(20),
                        null,
                    ),
            )
        val active =
            device.copy(
                state = CollaborationDeviceState.ACTIVE,
                version = 1,
                updatedAt = at.plusSeconds(20),
            )
        assertNull(status(active, succeeded, at.plusSeconds(20)).provisioning.retryNotBefore)
        val revoked =
            device.copy(
                state = CollaborationDeviceState.REVOKED,
                version = 1000000,
                updatedAt = at.plusSeconds(20),
                revokedAt = at.plusSeconds(20),
            )
        assertNull(status(revoked, queued, at.plusSeconds(20)).provisioning.retryNotBefore)
        assertNull(status(device.copy(version = 999999), queued, at).provisioning.retryNotBefore)
        assertNull(
            status(device, queued.copy(request = request.copy(revision = 999999)), at)
                .provisioning
                .retryNotBefore
        )
    }

    @Test
    fun foreignMissingContinuityAndFutureMetadataDoNotBecomeSetupStatus() {
        val bad =
            listOf(
                request.copy(spaceId = UUID.randomUUID()),
                request.copy(accountId = UUID.randomUUID()),
                request.copy(deviceId = UUID.randomUUID()),
                request.copy(nativeSessionId = UUID.randomUUID()),
                request.copy(client = ClientVersion(ClientPlatform.IOS, 28)),
                request.copy(requestedAt = at.plusSeconds(1)),
            )
        for (value in bad) assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceDeviceStatus(device, queued.copy(request = value), at),
        )
        for (value in
            listOf(
                device.copy(createdAt = at.plusSeconds(1)),
                device.copy(updatedAt = at.plusSeconds(1)),
                device.copy(state = CollaborationDeviceState.ACTIVE),
            )) assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceDeviceStatus(value, queued, at),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceDeviceStatus(
                device,
                queued.copy(state = CollaborationSpaceProvisioningState.SUCCEEDED),
                at,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceDeviceStatus(
                device,
                queued.copy(
                    lastOutcome =
                        CollaborationSpaceProvisioningOutcome(
                            0,
                            CollaborationSpaceProvisioningState.FAILED,
                            at.plusSeconds(1),
                            CollaborationSpaceProvisioningFailure.ACCESS_DENIED,
                        )
                ),
                at,
            ),
        )
    }

    private fun status(
        device: CollaborationSpaceDevice,
        head: CollaborationSpaceProvisioning,
        at: Instant,
    ): CollaborationSpaceDeviceStatus {
        val result = collaborationSpaceDeviceStatus(device, head, at)
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
