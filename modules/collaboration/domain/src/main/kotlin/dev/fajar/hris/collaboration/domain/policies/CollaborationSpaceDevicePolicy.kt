package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID

fun validateCollaborationSpaceDeviceRequest(
    origin: CollaborationSpaceDeviceOrigin,
    legacy: CollaborationLegacyDevicePosition?,
    fingerprint: String?,
    platform: CollaborationPlatform,
    label: String,
    client: ClientVersion?,
): Result<String> {
    val name = label.trim()
    if (
        name.length !in 1..64 ||
            name.any(Char::isISOControl) ||
            platform == CollaborationPlatform.WEB ||
            client?.platform?.name != platform.name
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_device"))
    if (
        if (origin == CollaborationSpaceDeviceOrigin.LEGACY)
            legacy == null || fingerprint?.matches(Regex("[a-f0-9]{64}")) != true
        else legacy != null || fingerprint != null
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_device"))
    return Result.Success(name)
}

/** Checks metadata continuity only; the client must independently reopen and match its SDK keys. */
fun validateCollaborationSpaceLegacyDevice(
    identity: CollaborationSpaceIdentity,
    source: CollaborationLegacyParent?,
    nativeSessionId: UUID,
    platform: CollaborationPlatform,
    legacy: CollaborationLegacyDevicePosition,
    fingerprint: String,
    now: Instant,
): Result<Unit> {
    val device = source?.device
    val key = source?.key
    if (
        source == null ||
            device == null ||
            key == null ||
            source.nativeSessionId != nativeSessionId ||
            device.accountId != identity.accountId ||
            device.position != legacy ||
            device.matrixUserId != identity.matrixUserId ||
            device.matrixDeviceId != collaborationMatrixDeviceId(legacy.deviceId) ||
            device.platform != platform ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.deviceRevokedAt != null ||
            device.credentialRetiredAt != null ||
            device.deviceCreatedAt > device.identityObservedAt ||
            device.identityObservedAt > now ||
            source.boundAt < device.deviceCreatedAt ||
            source.boundAt > now ||
            key.fingerprint != fingerprint ||
            key.version !in 0L..1L ||
            (key.version == 1L) != (key.publishedAt != null) ||
            key.reservedAt < device.deviceCreatedAt ||
            key.reservedAt > now ||
            key.publishedAt?.let { it < key.reservedAt || it > now } == true
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "collaboration_device_migration_required")
        )
    return Result.Success(Unit)
}

fun validateExistingCollaborationSpaceDevice(
    identity: CollaborationSpaceIdentity,
    device: CollaborationSpaceDevice,
    nativeSessionId: UUID,
    platform: CollaborationPlatform,
    origin: CollaborationSpaceDeviceOrigin,
    legacy: CollaborationLegacyDevicePosition?,
    fingerprint: String?,
    now: Instant,
): Result<Unit> {
    if (
        device.spaceId != identity.spaceId ||
            device.accountId != identity.accountId ||
            device.matrixUserId != identity.matrixUserId ||
            device.originNativeSessionId != nativeSessionId ||
            device.platform != platform ||
            device.origin != origin ||
            device.legacy != legacy ||
            device.legacyKeyFingerprint != fingerprint ||
            device.state == CollaborationDeviceState.REVOKED ||
            device.revokedAt != null ||
            device.version !in 0..999999 ||
            device.createdAt > device.updatedAt ||
            device.updatedAt > now ||
            device.matrixDeviceId !=
                (legacy?.let { collaborationMatrixDeviceId(it.deviceId) }
                    ?: "HRISG_" + device.id.toString().replace("-", ""))
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_device_changed"))
    return Result.Success(Unit)
}
