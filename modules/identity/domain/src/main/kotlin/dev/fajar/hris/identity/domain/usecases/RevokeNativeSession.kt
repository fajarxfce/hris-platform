package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class RevokeNativeSession(
    private val sessions: NativeSessionRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
) {
    fun execute(actor: Actor, sessionId: UUID): Result<Unit> =
        transactions.run(actor.copy(companyId = null)) {
            val account = sessions.lockAccount(actor.accountId)
            if (account is Result.Failed) return@run account
            val session = sessions.lock(sessionId, actor.accountId)
            if (session is Result.Failed) return@run session
            if ((session as Result.Success).value == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "native_session_not_found"))
            sessions.revoke(sessionId, actor.accountId, clock.instant()).flatMap { changed ->
                if (!changed) Result.Success(Unit)
                else
                    journal.record(
                        actor,
                        ChangeRecord("native_session", sessionId, "identity.native_session_revoked"),
                    )
            }
        }
}
