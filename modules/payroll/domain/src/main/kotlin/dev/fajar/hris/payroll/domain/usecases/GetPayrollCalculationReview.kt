package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetPayrollCalculationReview(
    private val periods: PayrollPeriodRepository,
    private val runs: PayrollRunRepository,
    private val sources: PayrollCalculationSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, periodId: UUID): Result<PayrollCalculationReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val periodResult = periods.find(company, periodId)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            val sourceResult =
                period?.let { sources.workPeriod(company, it.earningsMonth, lock = true) }
                    ?: Result.Success(null)
            if (sourceResult is Result.Failed) return@run sourceResult
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadPayrollInput(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            if (period == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "payroll_period_not_found"))
            val policyResult = policies.effective(company, period.earningsMonth)
            if (policyResult is Result.Failed) return@run policyResult
            val countResult = runs.count(company, periodId)
            if (countResult is Result.Failed) return@run countResult
            val pending = sources.pendingLeave(company, periodId, period.earningsMonth)
            if (pending is Result.Failed) return@run pending
            val source = (sourceResult as Result.Success).value
            val policy = (policyResult as Result.Success).value
            val count = (countResult as Result.Success).value
            val issues =
                payrollCalculationIssues(
                    period,
                    source,
                    policy,
                    count,
                    (pending as Result.Success).value,
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollCalculationReview(
                    period,
                    source,
                    policy,
                    count,
                    issues,
                    if ("payroll.calculate" in live.permissions && issues.isEmpty())
                        setOf(PayrollCalculationAction.START)
                    else emptySet(),
                )
            }
        }
    }
}
