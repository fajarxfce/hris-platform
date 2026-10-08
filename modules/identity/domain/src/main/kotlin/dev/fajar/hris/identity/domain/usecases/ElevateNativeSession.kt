package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** Accepts only a proof produced by a verified authentication use case, never client claims. */
class ElevateNativeSession(
    private val sessions: NativeSessionRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        expectedVersion: Long,
        proof: MfaProof,
        operationId: UUID,
    ): Result<NativeTokens> {
        if (actor.accountId != proof.accountId) return nativeAuthenticationRequired()
        return transactions.run(actor.copy(companyId = null)) {
            val locked = sessions.lockAccount(actor.accountId)
            if (locked is Result.Failed) return@run locked
            val account = (locked as Result.Success).value
            val found = sessions.lock(sessionId, actor.accountId)
            if (found is Result.Failed) return@run found
            val session =
                (found as Result.Success).value ?: return@run nativeAuthenticationRequired()
            if (session.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "native_session_changed"))
            val now = clock.instant()
            if (
                account == null ||
                    !account.active ||
                    account.securityVersion != proof.securityVersion ||
                    !nativeSessionActive(session, now) ||
                    proof.verifiedAt.isAfter(now) ||
                    !proof.verifiedAt.plusSeconds(60).isAfter(now) ||
                    session.version >= policy.maximumRotations
            )
                return@run nativeAuthenticationRequired()
            val updated =
                session.copy(
                    authenticatedAt = proof.verifiedAt,
                    mfaVerifiedAt = proof.verifiedAt,
                    credentialVersion = proof.securityVersion,
                    accessExpiresAt = minOf(now.plus(policy.accessLifetime), session.expiresAt),
                    rotatedAt = now,
                    version = session.version + 1,
                    exchangeReplayUntil = null,
                )
            sessions.rotate(updated, null, operationId, now.plus(policy.replayLifetime)).flatMap {
                tokens ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "native_session",
                            sessionId,
                            "identity.native_session_elevated",
                        ),
                    )
                    .map { tokens }
            }
        }
    }
}
