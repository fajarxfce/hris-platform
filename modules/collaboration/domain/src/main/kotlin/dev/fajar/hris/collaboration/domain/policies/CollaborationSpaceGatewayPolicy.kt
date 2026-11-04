package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

const val COLLABORATION_SPACE_GATEWAY_ACCESS_SECONDS = 300L
const val COLLABORATION_SPACE_GATEWAY_REPLAY_SECONDS = 30L
const val COLLABORATION_SPACE_GATEWAY_ROTATION_SECONDS = 10L

/** Validate parent/device admission and the credential response at the same final instant. */
fun validateCollaborationSpaceGatewayResponse(
    session: CollaborationSpaceGatewaySession,
    tokens: CollaborationSpaceGatewayTokens,
    admission: CollaborationSpaceAdmission,
    device: CollaborationSpaceDevice,
    at: Instant,
): Result<Unit> =
    validateCollaborationSpaceGatewaySession(session, admission, device, at).flatMap {
        if (session.clientBuild != admission.client.build)
            collaborationSpaceGatewayAuthenticationRequired()
        else validateCollaborationSpaceGatewayReplay(session, tokens, at)
    }

/** A retained credential head never substitutes for a fresh parent/device admission. */
fun validateCollaborationSpaceGatewaySession(
    session: CollaborationSpaceGatewaySession,
    admission: CollaborationSpaceAdmission,
    device: CollaborationSpaceDevice,
    at: Instant,
): Result<Unit> {
    if (
        session.spaceId != admission.spaceId ||
            session.accountId != admission.accountId ||
            session.deviceId != device.id ||
            session.nativeSessionId != admission.nativeSessionId ||
            session.platform != device.platform ||
            device.spaceId != admission.spaceId ||
            device.accountId != admission.accountId ||
            device.originNativeSessionId != admission.nativeSessionId ||
            device.platform.name != admission.client.platform.name ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.revokedAt != null ||
            device.version !in 0..999999 ||
            device.createdAt > device.updatedAt ||
            device.updatedAt > at ||
            device.createdAt > session.createdAt ||
            session.revokedAt != null ||
            session.version !in 0..999999 ||
            session.createdAt > session.rotatedAt ||
            session.rotatedAt > at ||
            session.expiresAt <= at ||
            session.clientBuild !in 1..999999999 ||
            session.accessExpiresAt > session.expiresAt ||
            session.accessExpiresAt <= session.rotatedAt ||
            admission.admittedAt > at ||
            admission.expiresAt <= at
    )
        return collaborationSpaceGatewayAuthenticationRequired()
    return Result.Success(Unit)
}

fun validateCollaborationSpaceGatewayReplay(
    session: CollaborationSpaceGatewaySession,
    tokens: CollaborationSpaceGatewayTokens,
    at: Instant,
): Result<Unit> {
    if (
        !validCollaborationSpaceGatewayAccess(tokens.accessToken) ||
            !validCollaborationSpaceGatewayRefresh(tokens.refreshToken) ||
            tokens.sessionId != session.id ||
            tokens.version != session.version ||
            tokens.accessExpiresAt != session.accessExpiresAt ||
            tokens.expiresAt != session.expiresAt ||
            session.rotatedAt > at ||
            tokens.replayUntil <= at ||
            tokens.accessExpiresAt <= at ||
            tokens.replayUntil >
                minOf(
                    session.rotatedAt.plusSeconds(COLLABORATION_SPACE_GATEWAY_REPLAY_SECONDS),
                    session.accessExpiresAt,
                )
    )
        return collaborationSpaceGatewayAuthenticationRequired()
    return Result.Success(Unit)
}

fun collaborationSpaceGatewayRotationAllowed(
    session: CollaborationSpaceGatewaySession,
    at: Instant,
): Result<Unit> {
    if (session.version >= 999999)
        return Result.Failed(
            Failure(FailureKind.UNAUTHENTICATED, "collaboration_space_gateway_renewal_required")
        )
    val earliest = session.rotatedAt.plusSeconds(COLLABORATION_SPACE_GATEWAY_ROTATION_SECONDS)
    if (at < earliest)
        return Result.Failed(
            Failure(
                FailureKind.RATE_LIMITED,
                "collaboration_space_gateway_rotation_too_soon",
                parameters =
                    mapOf(
                        "retryAfterSeconds" to
                            ((Duration.between(at, earliest).toMillis() + 999) / 1000).toString()
                    ),
            )
        )
    return Result.Success(Unit)
}

fun collaborationSpaceGatewayAuthenticationRequired() =
    Result.Failed(
        Failure(FailureKind.UNAUTHENTICATED, "collaboration_space_gateway_authentication_required")
    )
