package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import java.util.UUID

class ListManagedAccounts(
    private val accounts: AccountAdministrationRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<ManagedAccount>> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        if (query.length > 100 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return transactions.run(actor) { accounts.list(query.trim().lowercase(), after, limit) }
    }
}
