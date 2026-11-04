package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant

/** A status observation is never authority to retry, publish keys or acquire a provider token. */
fun collaborationSpaceDeviceStatus(
    device: CollaborationSpaceDevice,
    head: CollaborationSpaceProvisioning,
    at: Instant,
    gateway: CollaborationSpaceGatewaySession? = null,
): Result<CollaborationSpaceDeviceStatus> {
    if (
        gateway != null &&
            (gateway.spaceId != device.spaceId ||
                gateway.accountId != device.accountId ||
                gateway.deviceId != device.id ||
                gateway.nativeSessionId != device.originNativeSessionId ||
                gateway.platform != device.platform ||
                gateway.version !in 0..1000000 ||
                gateway.clientBuild !in 1..999999999 ||
                gateway.createdAt < device.createdAt ||
                gateway.rotatedAt < gateway.createdAt ||
                gateway.rotatedAt > at ||
                gateway.accessExpiresAt <= gateway.rotatedAt ||
                gateway.expiresAt < gateway.accessExpiresAt ||
                gateway.revokedAt?.let { it < gateway.rotatedAt || it > at } == true)
    )
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_collaboration_space_gateway"))
    val request = head.request
    if (
        device.spaceId != request.spaceId ||
            device.accountId != request.accountId ||
            device.id != request.deviceId ||
            device.originNativeSessionId != request.nativeSessionId ||
            device.platform.name != request.client.platform.name ||
            request.requestedAt > at ||
            device.createdAt > at ||
            device.updatedAt > at ||
            head.lastOutcome?.at?.let { it > at } == true ||
            (device.state == CollaborationDeviceState.ACTIVE &&
                head.state != CollaborationSpaceProvisioningState.SUCCEEDED) ||
            (device.state == CollaborationDeviceState.PROVISIONING &&
                head.state == CollaborationSpaceProvisioningState.SUCCEEDED)
    )
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning"))
    val pending =
        head.state in
            setOf(
                CollaborationSpaceProvisioningState.QUEUED,
                CollaborationSpaceProvisioningState.RUNNING,
            )
    val retry =
        if (
            device.state == CollaborationDeviceState.PROVISIONING &&
                device.version in 0..999998 &&
                request.revision in 0..999998 &&
                head.state != CollaborationSpaceProvisioningState.SUCCEEDED
        ) {
            val cooldown = request.requestedAt.plusSeconds(COLLABORATION_PROVISIONING_RETRY_SECONDS)
            if (pending) maxOf(cooldown, request.expiresAt) else cooldown
        } else null
    return Result.Success(
        CollaborationSpaceDeviceStatus(
            device.id,
            device.version,
            device.state,
            device.platform,
            device.origin,
            device.label,
            CollaborationSpaceProvisioningStatus(
                request.revision,
                head.state,
                head.attempts,
                request.expiresAt,
                head.lastOutcome,
                retry,
            ),
            at,
            gateway?.let {
                CollaborationSpaceGatewayStatus(
                    it.id,
                    it.version,
                    it.clientBuild,
                    it.accessExpiresAt,
                    it.expiresAt,
                    it.revokedAt,
                )
            },
        )
    )
}
