package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.canReadPayrollTaxOpening
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock

class ListPayrollTaxOpeningEmployees(
    private val openings: PayrollTaxOpeningRepository,
    private val policies: PayrollPolicyRepository,
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
        year: Int,
        query: String,
        status: PayrollTaxOpeningEmployeeStatus?,
        after: String?,
        limit: Int,
    ): Result<Page<PayrollTaxOpeningEmployee>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollTaxOpening(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (
            year !in 2024..2100 ||
                limit !in 1..50 ||
                query.length > 120 ||
                (after != null && (after.isBlank() || after.length > 32))
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
            if (!canReadPayrollTaxOpening((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            openings.employees(company, year, query.trim(), status, after, limit).flatMap { page ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { page }
            }
        }
    }
}
