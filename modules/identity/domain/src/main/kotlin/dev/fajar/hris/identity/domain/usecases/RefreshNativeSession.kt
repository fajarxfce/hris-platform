package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class RefreshNativeSession(
    private val sessions: NativeSessionRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
) {
    fun execute(token: String, operationId: UUID, correlationId: UUID): Result<NativeTokens> {
        if (!validNativeToken(token)) return nativeAuthenticationRequired()
        return sessions.findRefresh(token).flatMap { found ->
            if (found == null) return@flatMap nativeAuthenticationRequired()
            val initial = found.session
            val actor =
                Actor(initial.accountId, null, emptySet(), initial.authenticatedAt, correlationId)
            transactions
                .run<NativeRefreshOutcome>(actor) {
                    val accountResult = sessions.lockAccount(initial.accountId)
                    if (accountResult is Result.Failed) return@run accountResult
                    val account = (accountResult as Result.Success).value
                    val locked = sessions.lock(initial.id, initial.accountId)
                    if (locked is Result.Failed) return@run locked
                    val session =
                        (locked as Result.Success).value
                            ?: return@run nativeAuthenticationRequired()
                    val now = clock.instant()
                    if (
                        account == null ||
                            !account.active ||
                            session.credentialVersion != account.securityVersion ||
                            !nativeSessionActive(session, now)
                    )
                        return@run nativeAuthenticationRequired()
                    // The first lookup only identifies the lock. Re-read the token after
                    // serializing rotations.
                    val tokenResult = sessions.findRefresh(token)
                    if (tokenResult is Result.Failed) return@run tokenResult
                    val current =
                        (tokenResult as Result.Success).value
                            ?: return@run nativeAuthenticationRequired()
                    if (current.session.id != session.id) return@run nativeAuthenticationRequired()
                    if (current.consumed) {
                        if (
                            current.operationId == operationId &&
                                current.successorVersion == session.version &&
                                current.replayUntil?.isAfter(now) == true &&
                                session.accessExpiresAt.isAfter(now)
                        ) {
                            val replay = sessions.replayRefresh(token, session, operationId)
                            if (replay is Result.Failed) return@run replay
                            val tokens = (replay as Result.Success).value
                            if (tokens != null)
                                return@run Result.Success(NativeRefreshOutcome.Accepted(tokens))
                        }
                        return@run sessions.revoke(session.id, session.accountId, now).flatMap {
                            journal
                                .record(
                                    actor,
                                    ChangeRecord(
                                        "native_session",
                                        session.id,
                                        "identity.refresh_reuse_detected",
                                    ),
                                )
                                .map {
                                    NativeRefreshOutcome.Rejected(
                                        Failure(
                                            FailureKind.UNAUTHENTICATED,
                                            "native_session_revoked",
                                        )
                                    )
                                }
                        }
                    }
                    if (session.version >= policy.maximumRotations) {
                        return@run sessions.revoke(session.id, session.accountId, now).flatMap {
                            journal
                                .record(
                                    actor,
                                    ChangeRecord(
                                        "native_session",
                                        session.id,
                                        "identity.native_session_rotation_limit",
                                    ),
                                )
                                .map {
                                    NativeRefreshOutcome.Rejected(
                                        Failure(
                                            FailureKind.UNAUTHENTICATED,
                                            "native_session_renewal_required",
                                        )
                                    )
                                }
                        }
                    }
                    if (session.rotatedAt.plus(policy.minimumRotationInterval).isAfter(now))
                        return@run Result.Failed(
                            Failure(FailureKind.RATE_LIMITED, "native_refresh_too_soon")
                        )
                    val updated =
                        session.copy(
                            version = session.version + 1,
                            rotatedAt = now,
                            accessExpiresAt =
                                minOf(now.plus(policy.accessLifetime), session.expiresAt),
                            exchangeReplayUntil = null,
                        )
                    sessions
                        .rotate(updated, token, operationId, now.plus(policy.replayLifetime))
                        .flatMap { tokens ->
                            journal
                                .record(
                                    actor,
                                    ChangeRecord(
                                        "native_session",
                                        session.id,
                                        "identity.native_session_refreshed",
                                    ),
                                )
                                .map { NativeRefreshOutcome.Accepted(tokens) }
                        }
                }
                .flatMap { outcome ->
                    // Reuse revocation must commit before the transport receives an authentication
                    // failure.
                    when (outcome) {
                        is NativeRefreshOutcome.Accepted -> Result.Success(outcome.tokens)
                        is NativeRefreshOutcome.Rejected -> Result.Failed(outcome.failure)
                    }
                }
        }
    }
}
