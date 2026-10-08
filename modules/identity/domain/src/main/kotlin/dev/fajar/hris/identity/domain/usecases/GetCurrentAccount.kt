package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.CurrentAccount
import dev.fajar.hris.identity.domain.repositories.IdentityRepository

class GetCurrentAccount(
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor): Result<CurrentAccount> =
        transactions.run(actor.copy(companyId = null)) {
            identities.access(actor.accountId, null).flatMap { access ->
                if (access == null || !access.account.active)
                    Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
                else
                    identities.memberships(actor.accountId).map { companies ->
                        CurrentAccount(
                            access.account,
                            access.permissions,
                            companies.filter { it.memberActive && it.companyActive },
                        )
                    }
            }
        }
}
