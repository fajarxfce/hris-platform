package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class GetPayrollRunComparison(
    private val runs: PayrollRunRepository,
    private val comparisons: PayrollComparisonRepository,
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        baselineId: UUID,
        after: UUID?,
        limit: Int,
        changedOnly: Boolean,
    ): Result<PayrollRunComparison> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        if (id == baselineId)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_comparison"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if (!canReadPayrollInput((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val selected = mutableListOf<PayrollRun>()
            for (runId in listOf(baselineId, id)) {
                val found = runs.find(company, runId)
                if (found is Result.Failed) return@run found
                val run =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "payroll_run_not_found")
                        )
                if (
                    run.status !in setOf(PayrollRunStatus.CALCULATED, PayrollRunStatus.FINALIZED) ||
                        run.processed != run.totalEmployees ||
                        run.succeeded != run.totalEmployees ||
                        run.failed != 0
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_comparison_unavailable")
                    )
                selected += run
            }
            val baseline = selected[0]
            val current = selected[1]
            if (baseline.earningsMonth >= current.earningsMonth)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_payroll_comparison")
                )
            val loaded = comparisons.compare(company, baselineId, id, after, limit, changedOnly)
            if (loaded is Result.Failed) return@run loaded
            val data = (loaded as Result.Success).value
            if (
                data.summary.baselineEmployees != baseline.totalEmployees ||
                    data.summary.currentEmployees != current.totalEmployees
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "payroll_comparison_unavailable")
                )
            val now = clock.instant()
            validateCompanySessionActor(actor, access, now, security).map {
                PayrollRunComparison(baseline, current, data, changedOnly, now)
            }
        }
    }
}
