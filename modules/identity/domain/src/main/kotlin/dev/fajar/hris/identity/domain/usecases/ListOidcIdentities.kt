package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.OidcIdentity
import dev.fajar.hris.identity.domain.policies.validatePlatformCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.util.UUID

class ListOidcIdentities(
    private val links: OidcIdentityRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        accountId: UUID,
        after: UUID?,
        size: Int,
    ): Result<Page<OidcIdentity>> {
        if (
            actor.companyId != null ||
                (actor.accountId != accountId && "identity.manage" !in actor.permissions)
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        if (size !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page_size"))
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val checked =
                identities.access(actor.accountId, null).flatMap {
                    validatePlatformCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (actor.accountId != accountId && "identity.manage" !in live.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            links.list(accountId, after, size)
        }
    }
}
