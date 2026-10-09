package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock

class ListNativeSessions(
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
) {
    fun execute(actor: Actor): Result<List<NativeSession>> =
        transactions.run(actor.copy(companyId = null)) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            identities
                .access(actor.accountId, null)
                .flatMap { validatePlatformCommandActor(actor.copy(companyId = null), it) }
                .flatMap { sessions.list(actor.accountId, clock.instant(), policy.maximumSessions) }
        }
}
