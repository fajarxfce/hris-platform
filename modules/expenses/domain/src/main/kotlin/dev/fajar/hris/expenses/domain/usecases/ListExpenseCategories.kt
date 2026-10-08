package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCategory
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.LocalDate

class ListExpenseCategories(
    private val policies: ExpensePolicyRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): Result<Page<ExpenseCategory>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (limit !in 1..200 || (after?.length ?: 0) > 32 || asOf.year !in 1900..2200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateExpenseActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            if (!canReadExpensePolicies((current as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            policies.list(company, asOf, after, limit)
        }
    }
}
