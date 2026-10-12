package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class GetOvertimePlanningReview(
    private val schedules: ScheduleRepository,
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
        employeeId: UUID,
        workDate: LocalDate,
    ): Result<OvertimePlanningReview> {
        if (actor.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val scheduleGuard = schedules.lock(company, shared = true)
            if (scheduleGuard is Result.Failed) return@run scheduleGuard
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
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val configured =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(configured.timezone))
            val currentEmployment = people.find(company, employeeId, today)
            if (currentEmployment is Result.Failed) return@run currentEmployment
            if (!canManageOvertime(live, (currentEmployment as Result.Success).value, today))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val date = validateOvertimeWorkDate(workDate, today)
            if (date is Result.Failed) return@run date
            val employeeResult = people.find(company, employeeId, workDate)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!employee.terms.isWorkingOn(workDate))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "overtime_employment_inactive")
                )
            // Read-only availability: do not create a period or reserve a plan while reviewing.
            val period = periods.find(company, YearMonth.from(workDate))
            if (period is Result.Failed) return@run period
            (period as Result.Success).value?.let {
                val mutable = requireMutablePeriod(it)
                if (mutable is Result.Failed) return@run mutable
            }
            val calendar =
                schedules
                    .calendar(company, employeeId, workDate, workDate)
                    .flatMap(::resolveCalendar)
            if (calendar is Result.Failed) return@run calendar
            val day = (calendar as Result.Success).value.days.single()
            if (day is ScheduledDay.Unassigned)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "overtime_schedule_missing")
                )
            validateCompanySessionActor(actor, current, clock.instant(), security).map {
                OvertimePlanningReview(
                    WorkforceEmployeeReference(
                        employee.id,
                        employee.employeeNumber,
                        employee.person.legalName,
                        employee.terms.status,
                        employee.version,
                    ),
                    workDate,
                    today,
                    configured.timezone,
                    day,
                    overtimePlanningRevision(
                        company,
                        employeeId,
                        employee.version,
                        configured.timezone,
                        day,
                    ),
                )
            }
        }
    }
}
