package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID

/** Opaque wake admission does not require FCM registration or short interactive/MFA freshness. */
fun collaborationSpaceReadinessAdmission(
    checkpoint: CollaborationSpaceReadinessCheckpoint,
    target: CollaborationReadinessTarget,
    snapshot: CollaborationSpaceReadinessSnapshot,
    policy: CollaborationSpacePolicy,
    serverName: String,
    now: Instant,
): CollaborationSpaceReadinessAdmission? {
    if (!validCollaborationSpaceReadinessCheckpoint(checkpoint)) return null
    val lease = checkpoint.lease
    val position = target.position
    val after = checkpoint.after
    if (
        position.accountId == UUID(0, 0) ||
            position.deviceId == UUID(0, 0) ||
            target.nativeSessionId == UUID(0, 0) ||
            position.accountId == lease.publisherAccountId ||
            (after != null && position <= after) ||
            now < checkpoint.capturedAt ||
            now >= lease.until ||
            now >= checkpoint.expiresAt ||
            snapshot.currentSpaceId != lease.spaceId
    )
        return null
    val access = snapshot.access ?: return null
    val parent = snapshot.parent ?: return null
    val device = snapshot.device ?: return null
    val gateway = snapshot.gateway ?: return null
    val keys = snapshot.keys ?: return null
    val provider = snapshot.provider ?: return null
    if (
        access.account.id != position.accountId ||
            !access.account.active ||
            access.account.securityVersion != parent.credentialVersion ||
            parent.credentialVersion < 0 ||
            parent.id != target.nativeSessionId ||
            parent.accountId != position.accountId ||
            parent.revokedAt != null ||
            parent.expiresAt <= now ||
            parent.version < 0 ||
            parent.authenticatedAt > now ||
            parent.mfaVerifiedAt?.let { it > now } == true ||
            parent.createdAt > parent.rotatedAt ||
            parent.rotatedAt > now ||
            parent.accessExpiresAt > parent.expiresAt
    )
        return null
    if (
        device.spaceId != lease.spaceId ||
            device.accountId != position.accountId ||
            device.id != position.deviceId ||
            device.originNativeSessionId != target.nativeSessionId ||
            device != snapshot.bound ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.revokedAt != null ||
            device.createdAt < parent.createdAt ||
            device.platform !in setOf(CollaborationPlatform.ANDROID, CollaborationPlatform.IOS)
    )
        return null
    if (
        gateway.id == UUID(0, 0) ||
            gateway.spaceId != lease.spaceId ||
            gateway.accountId != position.accountId ||
            gateway.deviceId != position.deviceId ||
            gateway.nativeSessionId != target.nativeSessionId ||
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
        return null
    val client = ClientVersion(ClientPlatform.valueOf(device.platform.name), gateway.clientBuild)
    if (validateCollaborationSpaceClient(client, policy) !is Result.Success) return null
    val input = snapshot.selection ?: return null
    if (input.selected == null) return null
    val selected =
        selectCollaborationSpaceIdentity(lease.spaceId, position.accountId, serverName, input, now)
    if (selected !is Result.Success) return null
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
        return null
    if (
        (device.origin == CollaborationSpaceDeviceOrigin.SPACE &&
            (device.legacy != null || device.legacyKeyFingerprint != null)) ||
            (device.origin == CollaborationSpaceDeviceOrigin.LEGACY &&
                (device.legacy == null || device.legacyKeyFingerprint == null))
    )
        return null
    val published = keys.publishedAt ?: return null
    if (
        !validCollaborationSpaceKey(keys) ||
            keys.version != 1L ||
            keys.spaceId != lease.spaceId ||
            keys.accountId != position.accountId ||
            keys.deviceId != position.deviceId ||
            keys.nativeSessionId != parent.id ||
            keys.matrixUserId != device.matrixUserId ||
            keys.matrixDeviceId != device.matrixDeviceId ||
            keys.reservedAt < device.createdAt ||
            published > checkpoint.capturedAt
    )
        return null
    if (
        provider.spaceId != lease.spaceId ||
            provider.accountId != position.accountId ||
            provider.deviceId != position.deviceId ||
            provider.nativeSessionId != parent.id ||
            provider.matrixUserId != device.matrixUserId ||
            provider.matrixDeviceId != device.matrixDeviceId ||
            provider.generation !in 0L..999999L ||
            provider.clearedAt != null ||
            provider.createdAt < device.createdAt ||
            provider.createdAt > provider.updatedAt ||
            provider.updatedAt > now ||
            provider.expiresAt <= provider.updatedAt
    )
        return null
    if (device.origin == CollaborationSpaceDeviceOrigin.LEGACY) {
        if (
            validateCollaborationLegacyCompletionProvenance(
                identity,
                device,
                snapshot.legacyParent,
                snapshot.completion,
                snapshot.provisioning,
                parent.id,
                parent.version,
                device.platform,
                now,
            )
                !is Result.Success
        )
            return null
        val completion = requireNotNull(snapshot.completion)
        if (
            keys.fingerprint != device.legacyKeyFingerprint ||
                keys.fingerprint != completion.fingerprint ||
                keys.reservedAt < completion.completedAt ||
                provider.generation < completion.targetProviderGeneration ||
                provider.createdAt > completion.completedAt
        )
            return null
    }
    val owner =
        CollaborationSpaceHistoryOwner(
            lease.spaceId,
            position.accountId,
            position.deviceId,
            serverName,
            device.matrixUserId,
            device.matrixDeviceId,
            device.legacy,
        )
    if (!validCollaborationSpaceHistoryIdentity(owner)) return null
    return CollaborationSpaceReadinessAdmission(
        identity,
        owner,
        device.version,
        parent.version,
        parent.credentialVersion,
        gateway,
        keys,
        provider,
        now,
        minOf(
            now.plusSeconds(20),
            lease.until,
            checkpoint.expiresAt,
            parent.expiresAt,
            gateway.expiresAt,
        ),
    )
}

/**
 * A provider response cannot acknowledge a different parent, device, gateway or credential
 * generation.
 */
fun sameCollaborationSpaceReadinessAdmission(
    before: CollaborationSpaceReadinessAdmission,
    after: CollaborationSpaceReadinessAdmission,
    now: Instant,
): Boolean =
    before.identity == after.identity &&
        before.owner == after.owner &&
        before.deviceVersion == after.deviceVersion &&
        before.nativeSessionVersion == after.nativeSessionVersion &&
        before.credentialVersion == after.credentialVersion &&
        before.gateway == after.gateway &&
        before.keys == after.keys &&
        before.provider == after.provider &&
        before.observedAt <= after.observedAt &&
        after.observedAt <= now &&
        before.expiresAt > now &&
        after.expiresAt > now

/**
 * Expired/missing credentials are deferred by the owning use case; this policy never refreshes
 * them.
 */
fun collaborationSpaceReadinessCredential(
    admission: CollaborationSpaceReadinessAdmission,
    credential: CollaborationSpaceProviderSession?,
    now: Instant,
): Boolean =
    credential != null &&
        credential.state == admission.provider &&
        admission.observedAt <= now &&
        admission.expiresAt > now &&
        credential.state.expiresAt > now &&
        credential.accessToken.length in 1..2048 &&
        credential.accessToken.all { it.code in 33..126 }
