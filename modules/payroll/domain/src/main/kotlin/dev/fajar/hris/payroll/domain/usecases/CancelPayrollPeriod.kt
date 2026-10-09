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

class CancelPayrollPeriod(
    private val periods: PayrollPeriodRepository,
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
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        if (version !in 0..255 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_period"))
        val key =
            OperationKey(
                "payroll.period_cancel",
                operationId,
                listOf(id.toString(), version.toString(), reason),
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
            val permission =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.calculate",
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = periods.find(company, id)
            if (found is Result.Failed) return@run found
            val period =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_period_not_found")
                    )
            if (period.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (period.status != PayrollPeriodStatus.DRAFT)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_not_draft"))
            if (period.version == 255L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_period_revision_limit")
                )
            periods.cancel(company, period, actor.accountId, clock.instant(), reason).flatMap {
                receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_period",
                            id,
                            "payroll.period_cancelled",
                            reason = reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
