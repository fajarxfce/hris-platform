package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class PlanOvertimeRequest(
    private val overtime: OvertimeRepository,
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val schedules: ScheduleRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        employeeId: UUID,
        expectedEmploymentVersion: Long,
        workDate: LocalDate,
        requested: OvertimeInterval,
        reason: String,
    ): Result<MutationReceipt> {
        if (actor.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val valid = validateOvertimePlan(workDate, requested, reason, expectedEmploymentVersion)
        if (valid is Result.Failed) return valid
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "overtime.plan",
                operationId,
                listOf(
                    id.toString(),
                    employeeId.toString(),
                    expectedEmploymentVersion.toString(),
                    workDate.toString(),
                    requested.startsAt.toString(),
                    requested.endsAt.toString(),
                    requested.breakMinutes.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val calendarLock = schedules.lock(company)
            if (calendarLock is Result.Failed) return@run calendarLock
            val month = YearMonth.from(workDate)
            val periodResult = periods.lockMonth(company, month, false)
            if (periodResult is Result.Failed) return@run periodResult
            val lock = overtime.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in setOf("overtime.manage", "overtime.self.manage") })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val currentResult = people.find(company, employeeId, today)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (!canManageOvertime(live, current, today))
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "overtime_request_not_found")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val mutable = requireMutablePeriod((periodResult as Result.Success).value)
            if (mutable is Result.Failed) return@run mutable
            if (workDate < today.minusDays(31) || workDate > today.plusDays(90))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "overtime_date_out_of_range")
                )
            val employeeResult = people.find(company, employeeId, workDate)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.version != expectedEmploymentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            if (!employee.terms.isWorkingOn(workDate))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "overtime_employment_inactive")
                )
            val count = overtime.count(company, employeeId, month)
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 128)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "overtime_month_capacity"))
            val overlap = overtime.overlaps(company, employeeId, requested)
            if (overlap is Result.Failed) return@run overlap
            if ((overlap as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "overtime_overlap"))
            val calendar =
                schedules
                    .calendar(company, employeeId, workDate, workDate)
                    .flatMap(::resolveCalendar)
            if (calendar is Result.Failed) return@run calendar
            val day = (calendar as Result.Success).value.days.single()
            val time = validateOvertimeSchedule(workDate, settings.timezone, requested, day)
            if (time is Result.Failed) return@run time
            val request =
                OvertimeRequest(
                    id,
                    employeeId,
                    employee.employeeNumber,
                    employee.person.legalName,
                    employee.person.accountId,
                    actor.accountId,
                    now,
                    workDate,
                    settings.timezone,
                    day,
                    requested,
                    reason,
                )
            overtime.create(company, request).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "overtime_request",
                            id,
                            "overtime.planned",
                            mapOf(
                                "employeeId" to employeeId.toString(),
                                "workDate" to workDate.toString(),
                            ),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
