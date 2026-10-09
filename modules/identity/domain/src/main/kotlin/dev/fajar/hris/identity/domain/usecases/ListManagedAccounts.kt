package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.policies.validatePlatformCommandActor
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class ListManagedAccounts(
    private val accounts: AccountAdministrationRepository,
    private val identities: IdentityRepository,
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
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val checked =
                identities.access(actor.accountId, null).flatMap {
                    validatePlatformCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if ("identity.manage" !in live.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            accounts.list(query.trim().lowercase(), after, limit)
        }
    }
}
