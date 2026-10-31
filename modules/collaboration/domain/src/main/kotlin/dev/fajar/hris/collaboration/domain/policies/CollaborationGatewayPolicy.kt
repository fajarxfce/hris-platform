package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

const val COLLABORATION_GATEWAY_ACCESS_SECONDS = 300L
const val COLLABORATION_GATEWAY_REPLAY_SECONDS = 30L
const val COLLABORATION_GATEWAY_ROTATION_SECONDS = 10L

/** Validate parent/device admission and the credential response at the same final instant. */
fun validateCollaborationGatewayResponse(
    session: CollaborationGatewaySession,
    tokens: CollaborationGatewayTokens,
    admission: CollaborationAdmission,
    platform: CollaborationPlatform,
    at: Instant,
): Result<Unit> =
    validateCollaborationGatewaySession(session, admission, platform, at).flatMap {
        validateCollaborationGatewayReplay(session, tokens, at)
    }

/** A retained credential head never substitutes for a fresh parent/device admission. */
fun validateCollaborationGatewaySession(
    session: CollaborationGatewaySession,
    admission: CollaborationAdmission,
    platform: CollaborationPlatform,
    at: Instant,
): Result<Unit> {
    if (
        session.companyId != admission.companyId ||
            session.accountId != admission.accountId ||
            session.deviceId != admission.deviceId ||
            session.nativeSessionId != admission.nativeSessionId ||
            session.platform != platform ||
            session.revokedAt != null ||
            session.version !in 0..999999 ||
            session.createdAt > session.rotatedAt ||
            session.rotatedAt > at ||
            session.expiresAt <= at ||
            session.clientBuild <= 0 ||
            admission.admittedAt > at ||
            admission.expiresAt <= at
    )
        return collaborationGatewayAuthenticationRequired()
    return Result.Success(Unit)
}

fun validateCollaborationGatewayReplay(
    session: CollaborationGatewaySession,
    tokens: CollaborationGatewayTokens,
    at: Instant,
): Result<Unit> {
    if (
        tokens.sessionId != session.id ||
            tokens.version != session.version ||
            tokens.accessExpiresAt != session.accessExpiresAt ||
            tokens.expiresAt != session.expiresAt ||
            session.rotatedAt > at ||
            tokens.replayUntil <= at ||
            tokens.accessExpiresAt <= at ||
            tokens.replayUntil >
                minOf(
                    session.rotatedAt.plusSeconds(COLLABORATION_GATEWAY_REPLAY_SECONDS),
                    session.accessExpiresAt,
                )
    )
        return collaborationGatewayAuthenticationRequired()
    return Result.Success(Unit)
}

fun collaborationGatewayRotationAllowed(
    session: CollaborationGatewaySession,
    at: Instant,
): Result<Unit> {
    if (session.version >= 999999)
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "collaboration_gateway_renewal_required")
        )
    val earliest = session.rotatedAt.plusSeconds(COLLABORATION_GATEWAY_ROTATION_SECONDS)
    if (at < earliest)
        return Result.Failed(
            Failure(
                FailureKind.RATE_LIMITED,
                "collaboration_gateway_rotation_too_soon",
                parameters =
                    mapOf(
                        "retryAfterSeconds" to
                            ((Duration.between(at, earliest).toMillis() + 999) / 1000).toString()
                    ),
            )
        )
    return Result.Success(Unit)
}

fun collaborationGatewayAuthenticationRequired() =
    Result.Failed(
        Failure(FailureKind.UNAUTHENTICATED, "collaboration_gateway_authentication_required")
    )
