package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseCategory
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class SaveExpenseCategory(
    private val policies: ExpensePolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        category: ExpenseCategory,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val captured =
            category.copy(
                policy =
                    category.policy.copy(
                        allowedContracts = category.policy.allowedContracts.toSet()
                    )
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val valid = validateExpenseCategory(captured, reason)
        if (valid is Result.Failed) return valid
        if (expectedVersion != null && expectedVersion !in 0..999)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val policy = captured.policy
        val key =
            OperationKey(
                "expenses.category_save",
                operationId,
                listOf(
                    captured.id.toString(),
                    captured.code,
                    captured.effectiveFrom.toString(),
                    captured.active.toString(),
                    expectedVersion?.toString(),
                    reason,
                    policy.name,
                    policy.maximumLineAmount.stripTrailingZeros().toPlainString(),
                    policy.maximumClaimAmount.stripTrailingZeros().toPlainString(),
                    policy.receiptRequired.toString(),
                    policy.costCenterRequired.toString(),
                    policy.maximumAgeDays.toString(),
                    policy.allowedContracts.map { it.name }.sorted().joinToString(","),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val current = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (current is Result.Failed) return@run current
            val permission =
                (current as Result.Success).value.requirePermission("expenses.policy.manage")
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .map { _ -> it }
            }
            val found = policies.find(company, captured.id)
            if (found is Result.Failed) return@run found
            val existing = (found as Result.Success).value
            if (existing?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (existing != null && existing.code != captured.code)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "expense_category_code_immutable")
                )
            if (existing?.version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_policy_revision_limit")
                )
            if (existing == null) {
                val counted = policies.count(company)
                if (counted is Result.Failed) return@run counted
                if ((counted as Result.Success).value >= 500)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "expense_category_limit")
                    )
            }
            policies.save(actor, captured, expectedVersion, reason).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "expense_category",
                                captured.id,
                                "expenses.category_saved",
                                reason = reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateCompanySessionActor(actor, access, clock.instant(), security).map {
                            receipt
                        }
                    }
            }
        }
    }
}
