package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.Clock
import java.time.YearMonth

class ListWorkPeriodEmployees(
    private val periods: WorkPeriodRepository,
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
        month: YearMonth,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<WorkforceEmployeeReference>> {
        val permission = actor.requirePermission("workforce.read")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (month.year !in 2000..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        if (
            limit !in 1..50 ||
                query.length > 120 ||
                (after != null && (after.isBlank() || after.length > 32))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
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
            val current = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, current, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val access = (checked as Result.Success).value.requirePermission("workforce.read")
            if (access is Result.Failed) return@run access
            val found = periods.find(company, month)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            val jobId = period?.jobId
            if (period?.status != WorkPeriodStatus.CLOSED || jobId == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "work_period_snapshot_unavailable")
                )
            periods
                .publishedEmployees(
                    company,
                    jobId,
                    month.atEndOfMonth(),
                    query.trim(),
                    after,
                    limit,
                )
                .flatMap { page ->
                    validateCompanySessionActor(actor, current, clock.instant(), security).map {
                        page
                    }
                }
        }
    }
}
