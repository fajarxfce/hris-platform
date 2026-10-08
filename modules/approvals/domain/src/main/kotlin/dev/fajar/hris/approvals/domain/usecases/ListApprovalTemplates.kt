package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.LocalDate
import java.util.UUID

class ListApprovalTemplates(
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        kind: ApprovalKind,
        asOf: LocalDate,
        after: UUID?,
        limit: Int,
    ): Result<Page<ApprovalTemplate>> {
        if (limit !in 1..200 || asOf.year !in 1900..2200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { it.requirePermission("approvals.manage") }
                .flatMap { approvals.templatePage(company, kind, asOf, after, limit) }
        }
    }
}
