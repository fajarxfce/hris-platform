package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * Current guarded metadata only. Short native/gateway/MFA/provider expiry is not hard retirement.
 */
fun collaborationSpacePushTarget(
    binding: CollaborationSpacePushBinding,
    access: AccountAccess?,
    parent: NativeSession?,
    device: CollaborationSpaceDevice?,
    bound: CollaborationSpaceDevice?,
    gateway: CollaborationSpaceGatewaySession?,
    registration: NativePushRegistration?,
    currentSpaceId: UUID?,
    selection: CollaborationSpaceIdentityInput?,
    provider: CollaborationSpaceProviderState?,
    source: CollaborationLegacyParent?,
    completion: CollaborationLegacyRuntimeCompletion?,
    provisioning: CollaborationSpaceProvisioning?,
    policy: CollaborationSpacePolicy,
    serverName: String,
    now: Instant,
): CollaborationSpacePushTarget {
    val suppressed = CollaborationSpacePushTarget.Suppressed
    val retired = CollaborationSpacePushTarget.Retired
    if (
        !validCollaborationSpacePushBinding(binding) ||
            binding.reservedAt > now ||
            binding.publishedAt?.let { it > now } == true
    )
        return suppressed
    // Foreign or malformed projections cannot prove that this retained identity was retired.
    if (
        (access != null && access.account.id != binding.accountId) ||
            (parent != null &&
                (parent.id != binding.nativeSessionId || parent.accountId != binding.accountId)) ||
            (device != null &&
                (device.id != binding.deviceId ||
                    device.spaceId != binding.spaceId ||
                    device.accountId != binding.accountId ||
                    device.originNativeSessionId != binding.nativeSessionId))
    )
        return suppressed
    if (parent == null || device == null) return retired
    if (
        parent.revokedAt != null ||
            parent.expiresAt <= now ||
            device.revokedAt != null ||
            device.state == CollaborationDeviceState.REVOKED
    )
        return retired
    if (
        access != null &&
            parent.credentialVersion >= 0 &&
            access.account.securityVersion > parent.credentialVersion
    )
        return retired
    if (
        access == null ||
            !access.account.active ||
            currentSpaceId != binding.spaceId ||
            access.account.securityVersion != parent.credentialVersion ||
            parent.credentialVersion < 0 ||
            parent.version < 0 ||
            parent.authenticatedAt > now ||
            parent.mfaVerifiedAt?.let { it > now } == true ||
            parent.createdAt > parent.rotatedAt ||
            parent.rotatedAt > now ||
            parent.accessExpiresAt > parent.expiresAt ||
            device != bound ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.createdAt < parent.createdAt ||
            device.createdAt > binding.reservedAt ||
            device.matrixUserId != binding.matrixUserId ||
            device.matrixDeviceId != binding.matrixDeviceId ||
            device.platform !in setOf(CollaborationPlatform.ANDROID, CollaborationPlatform.IOS)
    )
        return suppressed
    if (
        gateway == null ||
            gateway.id == UUID(0, 0) ||
            gateway.spaceId != binding.spaceId ||
            gateway.accountId != binding.accountId ||
            gateway.deviceId != binding.deviceId ||
            gateway.nativeSessionId != binding.nativeSessionId ||
            gateway.platform != device.platform ||
            gateway.version !in 0L..999999L ||
            gateway.revokedAt != null ||
            gateway.createdAt < device.createdAt ||
            gateway.createdAt > gateway.rotatedAt ||
            gateway.rotatedAt > now ||
            gateway.expiresAt <= now ||
            gateway.expiresAt > parent.expiresAt ||
            gateway.accessExpiresAt > gateway.expiresAt ||
            gateway.accessExpiresAt <= gateway.rotatedAt
    )
        return suppressed
    val client = ClientVersion(ClientPlatform.valueOf(device.platform.name), gateway.clientBuild)
    if (validateCollaborationSpaceClient(client, policy) !is Result.Success) return suppressed
    if (selection?.selected == null) return suppressed
    val selected =
        selectCollaborationSpaceIdentity(
            binding.spaceId,
            binding.accountId,
            serverName,
            selection,
            now,
        )
    if (selected !is Result.Success) return suppressed
    val identity = selected.value
    if (
        validateExistingCollaborationSpaceDevice(
            identity,
            device,
            parent.id,
            device.platform,
            device.origin,
            device.legacy,
            device.legacyKeyFingerprint,
            now,
        )
            !is Result.Success
    )
        return suppressed
    if (
        (device.origin == CollaborationSpaceDeviceOrigin.SPACE &&
            (device.legacy != null || device.legacyKeyFingerprint != null)) ||
            (device.origin == CollaborationSpaceDeviceOrigin.LEGACY &&
                (device.legacy == null ||
                    device.legacyKeyFingerprint?.matches(Regex("[a-f0-9]{64}")) != true))
    )
        return suppressed
    if (
        provider == null ||
            provider.spaceId != binding.spaceId ||
            provider.accountId != binding.accountId ||
            provider.deviceId != binding.deviceId ||
            provider.nativeSessionId != binding.nativeSessionId ||
            provider.matrixUserId != binding.matrixUserId ||
            provider.matrixDeviceId != binding.matrixDeviceId ||
            provider.generation !in 0L..999999L ||
            provider.clearedAt != null ||
            provider.createdAt < device.createdAt ||
            provider.updatedAt < provider.createdAt ||
            provider.updatedAt > now ||
            provider.expiresAt <= provider.updatedAt
    )
        return suppressed
    if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
        if (
            validateCollaborationLegacyCompletionProvenance(
                identity,
                device,
                source,
                completion,
                provisioning,
                parent.id,
                parent.version,
                device.platform,
                now,
            )
                !is Result.Success
        )
            return suppressed
        val done = requireNotNull(completion)
        if (
            provider.generation < done.targetProviderGeneration ||
                provider.createdAt > done.completedAt
        )
            return suppressed
    }
    if (
        registration == null ||
            registration.accountId != binding.accountId ||
            registration.sessionId != binding.nativeSessionId ||
            !registration.enabled ||
            registration.platform.name != device.platform.name ||
            registration.version !in 0L..9999L ||
            registration.registeredAt < parent.createdAt ||
            registration.registeredAt > registration.updatedAt ||
            registration.updatedAt > now ||
            registration.expiresAt <= now
    )
        return suppressed
    val deadline =
        minOf(now.plusSeconds(30), parent.expiresAt, gateway.expiresAt, registration.expiresAt)
    return CollaborationSpacePushTarget.Ready(
        binding,
        identity,
        device,
        provider,
        parent.version,
        parent.credentialVersion,
        gateway,
        registration,
        now,
        deadline,
    )
}

/** Exact publication is immutable; only a pending reservation may advance during external I/O. */
fun sameCollaborationSpacePushBinding(
    before: CollaborationSpacePushBinding,
    after: CollaborationSpacePushBinding,
): Boolean =
    before == after ||
        (before.version == 0L &&
            after.version == 1L &&
            after.copy(version = 0L, publishedAt = null) == before &&
            validCollaborationSpacePushBinding(after))

/** Reacquire after I/O: old results must not acknowledge a changed device, gateway or token. */
fun sameCollaborationSpacePushTarget(
    before: CollaborationSpacePushTarget.Ready,
    after: CollaborationSpacePushTarget,
    now: Instant,
): Boolean =
    after is CollaborationSpacePushTarget.Ready &&
        before.observedAt <= after.observedAt &&
        after.observedAt <= now &&
        before.expiresAt > now &&
        after.expiresAt > now &&
        sameCollaborationSpacePushBinding(before.binding, after.binding) &&
        before.identity == after.identity &&
        before.device == after.device &&
        before.provider == after.provider &&
        before.nativeSessionVersion == after.nativeSessionVersion &&
        before.credentialVersion == after.credentialVersion &&
        before.gateway == after.gateway &&
        before.registration == after.registration
