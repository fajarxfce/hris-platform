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

class GetPayrollTaxOpeningReview(
    private val openings: PayrollTaxOpeningRepository,
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
        year: Int,
        historyAfter: Long?,
        historyLimit: Int,
    ): Result<PayrollTaxOpeningReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollTaxOpening(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            year !in 2024..2100 ||
                historyLimit !in 1..200 ||
                (historyAfter != null && historyAfter !in 0..999)
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
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
            if (!canReadPayrollTaxOpening(live))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val reference = openings.employee(company, employeeId)
            if (reference is Result.Failed) return@run reference
            val employee =
                (reference as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            val currentResult = openings.find(company, employeeId, year)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (historyAfter != null && (current == null || historyAfter > current.version))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            val history = openings.history(company, employeeId, year, historyAfter, historyLimit)
            if (history is Result.Failed) return@run history
            val authors =
                current?.let { openings.authors(company, it.id) } ?: Result.Success(emptySet())
            if (authors is Result.Failed) return@run authors
            val frozen =
                cutoffs.frozenMonths(
                    company,
                    employeeId,
                    (1..12).map { YearMonth.of(year, it) }.toSet(),
                )
            if (frozen is Result.Failed) return@run frozen
            val locked = (frozen as Result.Success).value
            return@run validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollTaxOpeningReview(
                    year,
                    employee,
                    current,
                    (history as Result.Success).value,
                    locked,
                    availablePayrollTaxOpeningActions(
                        live,
                        (beneficiary as Result.Success).value,
                        current,
                        (authors as Result.Success).value,
                        locked,
                    ),
                )
            }
        }
    }
}
