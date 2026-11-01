package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant

/**
 * Caller reads these current inputs under policy/company/member/account/parent/device guards. Short
 * access and MFA expiry do not stop opaque background hints. All content and reply operations
 * retain ordinary interactive admission. A retained binding is never authority.
 */
fun collaborationPushTarget(
    binding: CollaborationPushBinding,
    access: AccountAccess?,
    parent: NativeSession?,
    device: CollaborationDevice?,
    gateway: CollaborationGatewaySession?,
    registration: NativePushRegistration?,
    effective: EffectiveClientPolicy,
    now: Instant,
): CollaborationPushTarget {
    val suppressed = CollaborationPushTarget.Suppressed
    val retired = CollaborationPushTarget.Retired
    if (!validPushBinding(binding, now)) return suppressed
    // Inconsistent projections are not proof of permanent retirement. Fail closed without
    // deleting a potentially valid retained identity.
    if (
        (access != null && access.account.id != binding.accountId) ||
            (parent != null &&
                (parent.id != binding.nativeSessionId || parent.accountId != binding.accountId)) ||
            (device != null &&
                (device.id != binding.deviceId ||
                    device.companyId != binding.companyId ||
                    device.accountId != binding.accountId ||
                    device.nativeSessionId != binding.nativeSessionId))
    )
        return suppressed
    if (parent == null || device == null) return retired
    if (
        parent.revokedAt != null ||
            parent.expiresAt <= now ||
            device.state == CollaborationDeviceState.REVOKED ||
            device.revokedAt != null
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
            !access.companyActive ||
            !access.membershipActive ||
            COLLABORATION_CHAT_PERMISSION !in access.permissions ||
            parent.credentialVersion != access.account.securityVersion ||
            parent.credentialVersion < 0 ||
            parent.version < 0 ||
            parent.createdAt > parent.authenticatedAt ||
            parent.authenticatedAt > now ||
            parent.createdAt > parent.rotatedAt ||
            parent.rotatedAt > now ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.version !in 0L..999999L ||
            device.createdAt < parent.createdAt ||
            device.createdAt > binding.reservedAt ||
            device.platform !in setOf(CollaborationPlatform.ANDROID, CollaborationPlatform.IOS)
    )
        return suppressed
    if (
        gateway == null ||
            gateway.companyId != binding.companyId ||
            gateway.accountId != binding.accountId ||
            gateway.deviceId != binding.deviceId ||
            gateway.nativeSessionId != binding.nativeSessionId ||
            gateway.platform != device.platform ||
            gateway.revokedAt != null ||
            gateway.version !in 0L..999999L ||
            gateway.clientBuild <= 0 ||
            gateway.createdAt < device.createdAt ||
            gateway.createdAt > gateway.rotatedAt ||
            gateway.rotatedAt > now ||
            gateway.expiresAt <= now
    )
        return suppressed
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
    val client =
        ClientVersion(
            if (device.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
            else ClientPlatform.IOS,
            gateway.clientBuild,
        )
    val available = nativeCollaborationClientDeadline(effective, client, now)
    if (available !is Result.Success) return suppressed
    val deadline =
        minOf(
            now.plusSeconds(30),
            parent.expiresAt,
            gateway.expiresAt,
            registration.expiresAt,
            available.value,
        )
    if (deadline <= now) return suppressed
    return CollaborationPushTarget.Ready(
        binding,
        device.version,
        parent.version,
        parent.credentialVersion,
        gateway.version,
        registration,
        now,
        deadline,
    )
}

/**
 * Reacquire the same current owner/target after I/O; a late result cannot retire a rotated token.
 */
fun sameCollaborationPushTarget(
    prepared: CollaborationPushTarget.Ready,
    current: CollaborationPushTarget,
    now: Instant,
): Boolean =
    current is CollaborationPushTarget.Ready &&
        prepared.observedAt <= current.observedAt &&
        current.observedAt <= now &&
        prepared.expiresAt > now &&
        current.expiresAt > now &&
        prepared.binding.copy(
            version = current.binding.version,
            publishedAt = current.binding.publishedAt,
        ) == current.binding &&
        current.binding.version >= prepared.binding.version &&
        prepared.deviceVersion == current.deviceVersion &&
        prepared.nativeSessionVersion == current.nativeSessionVersion &&
        prepared.credentialVersion == current.credentialVersion &&
        prepared.gatewayVersion == current.gatewayVersion &&
        prepared.registration == current.registration

/** DIRECT ownership only. Removable group/context membership needs its own current ACL. */
fun collaborationPushRoomOwned(
    binding: CollaborationPushBinding,
    event: CollaborationPushEvent,
    room: CollaborationOwnedRoom?,
    serverName: String,
): Boolean =
    validCollaborationPushEvent(event) &&
        room != null &&
        room.companyId == binding.companyId &&
        room.serverName == serverName &&
        room.roomId == event.roomId &&
        room.firstAccountId != room.secondAccountId &&
        binding.accountId in setOf(room.firstAccountId, room.secondAccountId) &&
        room.creatorAccountId in setOf(room.firstAccountId, room.secondAccountId)

fun validCollaborationPushEvent(event: CollaborationPushEvent): Boolean =
    validPushIdentifier(event.roomId, '!') && validPushIdentifier(event.eventId, '$')

private fun validPushIdentifier(value: String, prefix: Char): Boolean =
    value.length in 2..255 && value.first() == prefix && value.all { it.code in 33..126 }

private fun validPushBinding(binding: CollaborationPushBinding, now: Instant): Boolean =
    binding.version in 0L..1L &&
        binding.reservedAt <= now &&
        ((binding.version == 1L) == (binding.publishedAt != null)) &&
        (binding.publishedAt?.let { it >= binding.reservedAt && it <= now } != false)
