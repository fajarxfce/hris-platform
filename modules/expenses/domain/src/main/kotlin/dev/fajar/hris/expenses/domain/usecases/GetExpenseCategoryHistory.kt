package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCategoryRevision
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class GetExpenseCategoryHistory(
    private val policies: ExpensePolicyRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpenseCategoryRevision>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..200 || (after != null && after !in 0..999))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val permission =
                (current as Result.Success).value.requirePermission("expenses.policy.manage")
            if (permission is Result.Failed) return@run permission
            val found = policies.find(company, id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "expense_category_not_found")
                )
            policies.history(company, id, after, limit)
        }
    }
}
