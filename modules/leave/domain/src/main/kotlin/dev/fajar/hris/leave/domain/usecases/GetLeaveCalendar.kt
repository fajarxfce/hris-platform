package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.authorizeLeaveCalendar
import dev.fajar.hris.leave.domain.repositories.LeaveCalendarRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class GetLeaveCalendar(
    private val calendar: LeaveCalendarRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, scope: LeaveCalendarScope, month: YearMonth?): Result<LeaveCalendar> {
        val admitted = authorizeLeaveCalendar(actor, scope)
        if (admitted is Result.Failed) return admitted
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (month != null && month.year !in 1900..2200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_leave_calendar_month"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permitted = authorizeLeaveCalendar(live, scope)
            if (permitted is Result.Failed) return@run permitted
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val current =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(current.timezone))
            val manager = if (scope == LeaveCalendarScope.TEAM) live.accountId else null
            val selected = month ?: YearMonth.from(today)
            val result = calendar.month(company, manager, today, selected)
            if (result is Result.Failed) return@run result
            val view =
                LeaveCalendar(
                    selected,
                    scope,
                    today,
                    current.timezone,
                    (result as Result.Success).value,
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { view }
        }
    }
}
