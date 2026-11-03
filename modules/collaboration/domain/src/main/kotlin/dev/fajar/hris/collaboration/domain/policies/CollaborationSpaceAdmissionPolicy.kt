package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant
import java.util.UUID

fun validateCollaborationSpaceClient(
    client: ClientVersion?,
    policy: CollaborationSpacePolicy,
): Result<Unit> {
    if (!policy.enabled)
        return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
    if (client == null || client.platform == ClientPlatform.WEB || client.build !in 1..999999999)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
    val minimum =
        if (client.platform == ClientPlatform.ANDROID) policy.minimumAndroidBuild
        else policy.minimumIosBuild
    if (client.build < minimum)
        return Result.Failed(
            Failure(
                FailureKind.FORBIDDEN,
                "client_update_required",
                parameters =
                    mapOf("platform" to client.platform.name, "minimumBuild" to minimum.toString()),
            )
        )
    return Result.Success(Unit)
}

/**
 * Account/session values must be read under their guards, with current group eligibility read
 * separately. Account-scope permissions never stand in for the group's directory eligibility. This
 * observation must not authorize a device, room, background job or future content exchange.
 */
fun admitCollaborationSpaceSession(
    actor: Actor,
    access: AccountAccess?,
    parent: NativeSession?,
    expectedSessionId: UUID,
    expectedSessionVersion: Long,
    currentSpaceId: UUID?,
    client: ClientVersion?,
    policy: CollaborationSpacePolicy,
    security: IdentitySecurityPolicy,
    now: Instant,
): Result<CollaborationSpaceAdmission> {
    val available = validateCollaborationSpaceClient(client, policy)
    if (available is Result.Failed) return available
    val live = validatePlatformSessionActor(actor, access, now, security)
    if (live is Result.Failed) return live
    val credentialVersion = actor.credentialVersion
    if (
        credentialVersion == null ||
            credentialVersion < 0 ||
            parent == null ||
            parent.id != expectedSessionId ||
            parent.accountId != actor.accountId ||
            parent.credentialVersion != credentialVersion ||
            parent.version != expectedSessionVersion ||
            expectedSessionVersion < 0 ||
            parent.authenticatedAt != actor.authenticatedAt ||
            parent.mfaVerifiedAt != actor.mfaVerifiedAt ||
            parent.authenticatedAt > now ||
            parent.mfaVerifiedAt?.let { it > now } == true ||
            parent.createdAt > parent.rotatedAt ||
            parent.rotatedAt > now ||
            parent.accessExpiresAt > parent.expiresAt ||
            !nativeSessionActive(parent, now) ||
            !parent.accessExpiresAt.isAfter(now)
    )
        return nativeAuthenticationRequired()
    if (currentSpaceId == null)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
    val currentAccess = requireNotNull(access)
    val mfaDeadline =
        if (
            security.enforceMfa &&
                (currentAccess.account.mfaConfigured ||
                    requiresMfa(currentAccess.securityPermissions))
        )
            requireNotNull(parent.mfaVerifiedAt).plus(security.maximumMfaAge)
        else Instant.MAX
    return Result.Success(
        CollaborationSpaceAdmission(
            currentSpaceId,
            actor.accountId,
            parent.id,
            parent.version,
            parent.credentialVersion,
            requireNotNull(client),
            now,
            minOf(now.plusSeconds(60), parent.accessExpiresAt, parent.expiresAt, mfaDeadline),
        )
    )
}
