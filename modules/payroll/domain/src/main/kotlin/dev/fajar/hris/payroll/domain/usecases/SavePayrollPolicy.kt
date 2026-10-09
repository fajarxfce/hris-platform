package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class SavePayrollPolicy(
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        policy: PayrollPolicy,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.policy.manage")
        if (access is Result.Failed) return access
        val valid = validatePayrollPolicy(policy, reason)
        if (valid is Result.Failed) return valid
        if (expectedVersion != null && expectedVersion !in 0..999)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.policy_save",
                operationId,
                listOf(
                    expectedVersion?.toString(),
                    policy.effectiveFrom.toString(),
                    policy.effectiveUntil.toString(),
                    policy.incomeTaxRuleId,
                    policy.insuranceRuleId,
                    policy.minimumMonthlyWage.stripTrailingZeros().toPlainString(),
                    policy.healthWageCap.stripTrailingZeros().toPlainString(),
                    policy.pensionWageCap.stripTrailingZeros().toPlainString(),
                    policy.contributionRounding.name,
                    reason,
                ) + policy.reviewReferences.sorted(),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock

            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value

            val permission =
                requirePayrollMutation(live, "payroll.policy.manage", clock.instant(), security)
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = policies.current(company)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current?.version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_policy_revision_limit")
                )
            policies.save(actor, policy, expectedVersion, reason).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_policy",
                            company,
                            "payroll.policy_saved",
                            reason = reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
