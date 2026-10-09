package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetNativePushRegistration(
    private val registrations: NativePushRegistrationRepository,
    private val sessions: NativeSessionRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
    ): Result<NativePushRegistration> =
        transactions.run(actor.copy(companyId = null)) {
            val account = sessions.lockAccount(actor.accountId)
            if (account is Result.Failed) return@run account
            val session = sessions.lock(sessionId, actor.accountId)
            if (session is Result.Failed) return@run session
            validateNativePushActor(
                    actor,
                    (account as Result.Success).value,
                    (session as Result.Success).value,
                    sessionVersion,
                    clock.instant(),
                )
                .flatMap {
                    registrations.find(actor.accountId, sessionId).flatMap { registration ->
                        if (registration == null)
                            Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "push_registration_not_found")
                            )
                        else Result.Success(registration)
                    }
                }
        }
}
