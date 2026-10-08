package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant

fun validNativeToken(token: String): Boolean = token.matches(Regex("[A-Za-z0-9_-]{43}"))

fun nativeSessionActive(session: NativeSession, now: Instant): Boolean =
    session.revokedAt == null && session.expiresAt.isAfter(now) && !session.createdAt.isAfter(now)

fun nativeAccess(session: NativeSession): NativeAccess =
    NativeAccess(
        session.id,
        session.accountId,
        session.authenticatedAt,
        session.mfaVerifiedAt,
        session.credentialVersion,
        session.version,
    )

fun nativeAuthenticationRequired(): Result.Failed =
    Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "native_session_invalid"))
