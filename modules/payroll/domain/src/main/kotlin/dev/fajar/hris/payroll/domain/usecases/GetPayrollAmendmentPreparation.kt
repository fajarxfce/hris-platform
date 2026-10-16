package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentPreparation
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetPayrollAmendmentPreparation(
    private val policies: PayrollPolicyRepository,
    private val sources: PayrollAmendmentSourceRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, assessmentId: UUID): Result<PayrollAmendmentPreparation> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.amend")
        if (permission is Result.Failed) return permission
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val workGuard = sources.lockWorkPeriods(company, assessmentId)
            if (workGuard is Result.Failed) return@run workGuard
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
            val allowed = (checked as Result.Success).value.requirePermission("payroll.amend")
            if (allowed is Result.Failed) return@run allowed
            val acquired = sources.sources(company, assessmentId)
            if (acquired is Result.Failed) return@run acquired
            val year = (acquired as Result.Success).value
            val first =
                year.firstOrNull { it.assessmentId == assessmentId }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, first.target.employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                PayrollAmendmentPreparation(assessmentId, year)
            }
        }
    }
}
