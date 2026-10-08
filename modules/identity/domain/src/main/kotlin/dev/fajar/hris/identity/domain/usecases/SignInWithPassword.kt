package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class SignInWithPassword(
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(email: String, password: String, correlationId: UUID): Result<Account> {
        if (email.length > 254 || password.length !in 1..128) {
            return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials"))
        }
        return identities.verifyPassword(email.trim().lowercase(), password).flatMap { account ->
            if (account == null || !account.active) {
                Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials"))
            } else {
                val actor = Actor(account.id, null, emptySet(), clock.instant(), correlationId)
                transactions.run(actor) {
                    identities.access(account.id, null).flatMap { current ->
                        if (
                            current == null ||
                                !current.account.active ||
                                current.account.version != account.version
                        ) {
                            Result.Failed(
                                Failure(FailureKind.UNAUTHENTICATED, "invalid_credentials")
                            )
                        } else {
                            journal
                                .record(
                                    actor,
                                    ChangeRecord(
                                        "account",
                                        account.id,
                                        "identity.password_verified",
                                    ),
                                )
                                .map { account }
                        }
                    }
                }
            }
        }
    }
}
