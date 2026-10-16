package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class ListManagedAccounts(
    private val accounts: AccountAdministrationRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
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
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validatePlatformSessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if ("identity.manage" !in live.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            accounts.list(query.trim().lowercase(), after, limit).flatMap { page ->
                validatePlatformSessionActor(actor, access, clock.instant(), security).map { page }
            }
        }
    }
}
