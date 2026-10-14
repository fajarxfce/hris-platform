package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.CompensationReview
import dev.fajar.hris.payroll.domain.policies.canReadCompensation
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

class GetCompensationReview(
    private val policies: PayrollPolicyRepository,
    private val compensations: CompensationRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        month: YearMonth,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<CompensationReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadCompensation(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (month.year !in 2024..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_month"))
        if (historyLimit !in 1..200 || (historyAfter != null && historyAfter !in 0..999))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val found = identities.access(actor.accountId, company)
            if (found is Result.Failed) return@run found
            val access = (found as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (!canReadCompensation(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val reference = compensations.employee(company, employeeId)
            if (reference is Result.Failed) return@run reference
            val employee =
                (reference as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            val permitted =
                "payroll.compensation.manage" in live.permissions &&
                    (beneficiary as Result.Success).value != live.accountId
            val currentResult = compensations.current(company, employeeId)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (historyAfter != null && (current == null || historyAfter > current.version))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val selected = compensations.effective(company, employeeId, month)
            if (selected is Result.Failed) return@run selected
            val effective = (selected as Result.Success).value
            compensations.history(company, employeeId, historyAfter, historyLimit).flatMap { history
                ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map {
                    CompensationReview(month, employee, current, effective, history, permitted)
                }
            }
        }
    }
}
