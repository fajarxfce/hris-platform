package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.OidcIdentity
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.util.UUID

class ListOidcIdentities(
    private val links: OidcIdentityRepository,
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
        return transactions.run(actor) { links.list(accountId, after, size) }
    }
}
