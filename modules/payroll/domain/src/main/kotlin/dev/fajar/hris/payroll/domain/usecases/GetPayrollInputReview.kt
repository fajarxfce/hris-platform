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
import java.time.YearMonth
import java.util.UUID

class GetPayrollInputReview(
    private val inputs: PayrollInputRepository,
    private val sources: PayrollWorkSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val cutoffs: PayrollCutoffRepository,
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
    ): Result<PayrollInputReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollInput(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            month.year !in 2024..2100 ||
                historyLimit !in 1..200 ||
                (historyAfter != null && historyAfter !in 0..999)
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val sourceResult = sources.find(company, employeeId, month, lock = true)
            if (sourceResult is Result.Failed) return@run sourceResult
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
            if (!canReadPayrollInput(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val reference = inputs.employee(company, employeeId)
            if (reference is Result.Failed) return@run reference
            val employee =
                (reference as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            val currentResult = inputs.find(company, employeeId, month)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (historyAfter != null && (current == null || historyAfter > current.version))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val history = inputs.history(company, employeeId, month, historyAfter, historyLimit)
            if (history is Result.Failed) return@run history
            val authors =
                current?.let { inputs.authors(company, it.id) } ?: Result.Success(emptySet())
            if (authors is Result.Failed) return@run authors
            val frozen = cutoffs.frozenMonths(company, employeeId, setOf(month))
            if (frozen is Result.Failed) return@run frozen
            val source = (sourceResult as Result.Success).value
            val locked = (frozen as Result.Success).value
            return@run validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollInputReview(
                    month,
                    employee,
                    source,
                    current,
                    (history as Result.Success).value,
                    locked,
                    availablePayrollInputActions(
                        live,
                        employee,
                        (beneficiary as Result.Success).value,
                        source,
                        current,
                        (authors as Result.Success).value,
                        locked,
                    ),
                )
            }
        }
    }
}
