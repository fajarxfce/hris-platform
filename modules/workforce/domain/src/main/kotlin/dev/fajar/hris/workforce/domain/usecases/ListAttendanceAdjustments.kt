package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.canReadWorkforce
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class ListAttendanceAdjustments(
    private val periods: WorkPeriodRepository,
    private val adjustments: AttendanceAdjustmentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        month: YearMonth,
        before: Long?,
        limit: Int,
    ): Result<Page<AttendanceAdjustmentSummary>> {
        if (month.year !in 2000..2100 || limit !in 1..50 || (before != null && before < 1))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val found = people.find(company, employeeId, month.atEndOfMonth())
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val current = people.findAtInstant(company, employeeId, clock.instant())
            if (current is Result.Failed) return@run current
            if (
                !canReadWorkforce(
                    (checked as Result.Success).value,
                    employee,
                    (current as Result.Success).value,
                )
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val periodResult = periods.find(company, month)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            val job = period?.jobId
            val result =
                if (period?.status == WorkPeriodStatus.CLOSED && job != null)
                    adjustments.history(company, job, employeeId, before, limit)
                else Result.Success(Page(emptyList(), null))
            result.flatMap { page ->
                validateCompanySessionActor(actor, access, clock.instant(), security).map { page }
            }
        }
    }
}
