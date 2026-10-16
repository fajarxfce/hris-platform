package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentIdentityAdministration
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.NativeSessionRepository
import java.time.Clock
import java.util.UUID

/** Ends the immutable session family, including a token rotated after review. */
class RevokeAccountNativeSession(
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, accountId: UUID, sessionId: UUID): Result<Unit> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        val recent = requireRecentIdentityAdministration(actor, clock.instant(), security)
        if (recent is Result.Failed) return recent
        return transactions.run(actor) {
            for (id in setOf(actor.accountId, accountId).sorted()) {
                val guard = identities.lockAccount(id)
                if (guard is Result.Failed) return@run guard
            }
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validatePlatformSessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if ("identity.manage" !in (checked as Result.Success).value.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            val currentProof = requireRecentIdentityAdministration(actor, clock.instant(), security)
            if (currentProof is Result.Failed) return@run currentProof
            val acquiredSession = sessions.lock(sessionId, accountId)
            if (acquiredSession is Result.Failed) return@run acquiredSession
            if ((acquiredSession as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "native_session_not_found"))
            sessions
                .revoke(sessionId, accountId, clock.instant())
                .flatMap { changed ->
                    if (!changed) Result.Success(Unit)
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "native_session",
                                sessionId,
                                "identity.native_session_revoked",
                                mapOf("accountId" to accountId.toString()),
                            ),
                        )
                }
                .flatMap { validatePlatformSessionActor(actor, access, clock.instant(), security) }
                .flatMap { requireRecentIdentityAdministration(actor, clock.instant(), security) }
        }
    }
}
