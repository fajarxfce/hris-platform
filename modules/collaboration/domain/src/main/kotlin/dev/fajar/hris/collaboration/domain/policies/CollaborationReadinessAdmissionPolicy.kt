package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant

/** No push registration or short access/MFA freshness is required for an opaque control. */
fun collaborationReadinessAdmission(
    control: CollaborationReadinessControl,
    access: AccountAccess?,
    parent: NativeSession?,
    device: CollaborationDevice?,
    gateway: CollaborationGatewaySession?,
    keys: CollaborationDeviceKeyPublication?,
    effective: EffectiveClientPolicy,
    now: Instant,
): CollaborationReadinessAdmission? {
    val publication = Instant.ofEpochMilli(control.publishedAtEpochMillis)
    if (publication > now || publication.plusSeconds(86400) <= now) return null
    if (access == null || parent == null || device == null || gateway == null || keys == null)
        return null
    if (
        access.account.id != control.accountId ||
            !access.account.active ||
            !access.companyActive ||
            !access.membershipActive ||
            COLLABORATION_CHAT_PERMISSION !in access.permissions
    )
        return null
    if (
        parent.id != control.nativeSessionId ||
            parent.accountId != control.accountId ||
            parent.revokedAt != null ||
            parent.expiresAt <= now ||
            parent.credentialVersion < 0 ||
            parent.credentialVersion != access.account.securityVersion ||
            parent.version < 0 ||
            parent.authenticatedAt > now ||
            parent.createdAt > parent.rotatedAt ||
            parent.rotatedAt > now
    )
        return null
    if (
        device.id != control.deviceId ||
            device.companyId != control.companyId ||
            device.accountId != control.accountId ||
            device.nativeSessionId != control.nativeSessionId ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.revokedAt != null ||
            device.version !in 0L..999999L ||
            device.createdAt < parent.createdAt ||
            device.createdAt > now ||
            device.platform !in setOf(CollaborationPlatform.ANDROID, CollaborationPlatform.IOS)
    )
        return null
    if (
        gateway.companyId != control.companyId ||
            gateway.accountId != control.accountId ||
            gateway.deviceId != control.deviceId ||
            gateway.nativeSessionId != control.nativeSessionId ||
            gateway.platform != device.platform ||
            gateway.revokedAt != null ||
            gateway.version !in 0L..999999L ||
            gateway.clientBuild <= 0 ||
            gateway.createdAt < device.createdAt ||
            gateway.createdAt > gateway.rotatedAt ||
            gateway.rotatedAt > now ||
            gateway.expiresAt <= now
    )
        return null
    if (
        keys.companyId != control.companyId ||
            keys.accountId != control.accountId ||
            keys.deviceId != control.deviceId ||
            keys.version != 1L ||
            keys.publishedAt == null ||
            keys.reservedAt > keys.publishedAt ||
            keys.publishedAt.toEpochMilli() > control.publishedAtEpochMillis ||
            keys.fingerprint.length != 64 ||
            keys.fingerprint.any { it !in '0'..'9' && it !in 'a'..'f' }
    )
        return null
    val client =
        ClientVersion(
            if (device.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
            else ClientPlatform.IOS,
            gateway.clientBuild,
        )
    val available = nativeCollaborationClientDeadline(effective, client, now)
    if (available !is Result.Success) return null
    val deadline =
        minOf(
            now.plusSeconds(20),
            parent.expiresAt,
            gateway.expiresAt,
            available.value,
            publication.plusSeconds(86400),
        )
    if (deadline <= now) return null
    return CollaborationReadinessAdmission(
        device.version,
        parent.version,
        parent.credentialVersion,
        gateway.version,
        now,
        deadline,
    )
}

fun sameCollaborationReadinessAdmission(
    before: CollaborationReadinessAdmission,
    after: CollaborationReadinessAdmission,
    now: Instant,
): Boolean =
    before.deviceVersion == after.deviceVersion &&
        before.parentVersion == after.parentVersion &&
        before.credentialVersion == after.credentialVersion &&
        before.gatewayVersion == after.gatewayVersion &&
        before.observedAt <= after.observedAt &&
        after.observedAt <= now &&
        before.expiresAt > now &&
        after.expiresAt > now

/** Missing/expired credentials request a bounded retry; this never creates a new provider login. */
fun collaborationReadinessCredential(
    control: CollaborationReadinessControl,
    credential: CollaborationProviderSession?,
    serverName: String,
    now: Instant,
): Boolean =
    credential != null &&
        credential.companyId == control.companyId &&
        credential.accountId == control.accountId &&
        credential.deviceId == control.deviceId &&
        credential.matrixUserId ==
            "@${collaborationMatrixLocalpart(control.companyId, control.accountId)}:$serverName" &&
        credential.matrixDeviceId == collaborationMatrixDeviceId(control.deviceId) &&
        credential.generation in 0L..999999L &&
        credential.createdAt <= credential.updatedAt &&
        credential.updatedAt <= now &&
        credential.expiresAt != null &&
        credential.expiresAt > now &&
        credential.accessToken.length in 1..2048 &&
        credential.accessToken.all { it.code in 33..126 }
