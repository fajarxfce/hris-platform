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

class ListDocumentInventories(
    private val inventory: DocumentInventoryRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<DocumentInventoryRun>> {

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
                    if (limit !in 1..100)
                        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page_size"))
                    else inventory.list(company, after, limit)
                }
        }
    }
}
