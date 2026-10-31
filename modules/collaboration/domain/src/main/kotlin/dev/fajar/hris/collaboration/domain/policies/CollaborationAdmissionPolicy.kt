package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant

const val COLLABORATION_CHAT_PERMISSION = "collaboration.chat"

/**
 * Call only with authority read under the company/member/account/session/device guards. Never
 * substitute an old client permission snapshot for live access.
 */
fun admitCollaborationDevice(
    actor: Actor,
    access: AccountAccess?,
    session: NativeSession?,
    expectedSessionVersion: Long,
    device: CollaborationDevice?,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<CollaborationAdmission> {
    val company = actor.companyId ?: return collaborationDenied()
    val deadline =
        collaborationParentDeadline(actor, access, session, expectedSessionVersion, now, security)
    if (deadline is Result.Failed) return deadline
    val parent = requireNotNull(session)
    if (
        device == null ||
            device.accountId != actor.accountId ||
            device.companyId != company ||
            device.nativeSessionId != parent.id ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.revokedAt != null ||
            device.version < 0 ||
            device.createdAt.isAfter(now)
    )
        return collaborationDenied()

    return Result.Success(
        CollaborationAdmission(
            company,
            actor.accountId,
            device.id,
            device.version,
            parent.id,
            parent.version,
            parent.credentialVersion,
            now,
            (deadline as Result.Success).value,
        )
    )
}

/** Shared parent admission for registration and transport-grant acquisition. */
internal fun collaborationParentDeadline(
    actor: Actor,
    access: AccountAccess?,
    session: NativeSession?,
    expectedSessionVersion: Long,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<Instant> {
    if (actor.companyId == null) return collaborationDenied()
    val authorized =
        validateCompanySessionActor(actor, access, now, security).flatMap {
            it.requirePermission(COLLABORATION_CHAT_PERMISSION)
        }
    if (authorized is Result.Failed) return authorized
    if (
        session == null ||
            session.accountId != actor.accountId ||
            session.credentialVersion != actor.credentialVersion ||
            session.version != expectedSessionVersion ||
            expectedSessionVersion < 0 ||
            !nativeSessionActive(session, now) ||
            !session.accessExpiresAt.isAfter(now)
    )
        return nativeAuthenticationRequired()
    val live = requireNotNull(access)
    val mfaDeadline =
        if (
            security.enforceMfa &&
                (live.account.mfaConfigured || requiresMfa(live.securityPermissions))
        )
            requireNotNull(actor.mfaVerifiedAt).plus(security.maximumMfaAge)
        else Instant.MAX
    return Result.Success(
        minOf(
            now.plusSeconds(60),
            requireNotNull(session).expiresAt,
            session.accessExpiresAt,
            mfaDeadline,
        )
    )
}

/**
 * Strict HRIS deadline: the provider's JWT clock tolerance grants no extra time. Reacquire
 * admission after external I/O before exposing any provider result.
 */
fun validateCollaborationExchange(
    prepared: CollaborationAdmission,
    current: CollaborationAdmission,
    now: Instant,
): Result<Unit> {
    if (
        prepared.admittedAt.isAfter(now) ||
            current.admittedAt.isAfter(now) ||
            current.admittedAt.isBefore(prepared.admittedAt) ||
            !prepared.expiresAt.isAfter(now) ||
            !current.expiresAt.isAfter(now) ||
            prepared.companyId != current.companyId ||
            prepared.accountId != current.accountId ||
            prepared.deviceId != current.deviceId ||
            prepared.deviceVersion != current.deviceVersion ||
            prepared.nativeSessionId != current.nativeSessionId ||
            prepared.nativeSessionVersion != current.nativeSessionVersion ||
            prepared.credentialVersion != current.credentialVersion
    )
        return collaborationDenied()
    return Result.Success(Unit)
}

private fun collaborationDenied() =
    Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
