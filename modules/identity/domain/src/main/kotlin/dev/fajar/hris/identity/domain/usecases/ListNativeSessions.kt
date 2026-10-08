package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock

class ListNativeSessions(
    private val sessions: NativeSessionRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
) {
    fun execute(actor: Actor): Result<List<NativeSession>> =
        transactions.run(actor.copy(companyId = null)) {
            sessions.list(actor.accountId, clock.instant(), policy.maximumSessions)
        }
}
