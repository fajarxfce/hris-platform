package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class GetDocumentInventoryPages(
    private val inventory: DocumentInventoryRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Int,
        limit: Int,
    ): Result<List<DocumentInventoryPage>> {

        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                .flatMap { live -> live.requirePermission("documents.inventory") }
                .flatMap {
                    if (after !in 0..10000 || limit !in 1..100)
                        return@flatMap Result.Failed(
                            Failure(FailureKind.VALIDATION, "invalid_page_size")
                        )
                    inventory.find(company, id).flatMap { value ->
                        if (value == null)
                            Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "document_inventory_not_found")
                            )
                        else inventory.pages(company, id, after, limit)
                    }
                }
        }
    }
}
