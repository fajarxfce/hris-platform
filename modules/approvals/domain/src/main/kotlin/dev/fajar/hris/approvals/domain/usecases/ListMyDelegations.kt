package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.Delegation
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class ListMyDelegations(
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<Delegation>> {
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { it.requirePermission("approvals.read") }
                .flatMap {
                    approvals.delegationPage(
                        company,
                        actor.accountId,
                        clock.instant(),
                        after,
                        limit,
                    )
                }
        }
    }
}
