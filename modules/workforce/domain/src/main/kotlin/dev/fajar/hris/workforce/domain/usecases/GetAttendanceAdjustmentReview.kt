package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class GetAttendanceAdjustmentReview(
    private val periods: WorkPeriodRepository,
    private val adjustments: AttendanceAdjustmentRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
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
        date: LocalDate,
    ): Result<AttendanceAdjustmentReview> {
        if (date.year !in 2000..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val locked = periods.lockExisting(company, YearMonth.from(date), exclusive = false)
            if (locked is Result.Failed) return@run locked
            val dayGuard = attendance.lockDay(company, employeeId, date, shared = true)
            if (dayGuard is Result.Failed) return@run dayGuard
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
            val live = (checked as Result.Success).value
            val found = people.find(company, employeeId, date)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val currentEmployee = people.findAtInstant(company, employeeId, clock.instant())
            if (currentEmployee is Result.Failed) return@run currentEmployee
            val current = (currentEmployee as Result.Success).value
            if (!canReadWorkforce(live, employee, current))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val period = (locked as Result.Success).value
            val jobId = period?.jobId
            if (period?.status != WorkPeriodStatus.CLOSED || jobId == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_requires_closed_period")
                )
            val originalResult = periods.snapshot(company, jobId, employeeId)
            if (originalResult is Result.Failed) return@run originalResult
            val original =
                (originalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "work_period_snapshot_unavailable")
                    )
            val previousResult = adjustments.latest(company, jobId, employeeId)
            if (previousResult is Result.Failed) return@run previousResult
            val previous = (previousResult as Result.Success).value
            val entriesResult = attendance.entries(company, employeeId, date, date)
            if (entriesResult is Result.Failed) return@run entriesResult
            val entries = (entriesResult as Result.Success).value
            if (entries.size > 32)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid")
                )
            val correctionResult = corrections.latest(company, employeeId, date, date)
            if (correctionResult is Result.Failed) return@run correctionResult
            val correction = (correctionResult as Result.Success).value.singleOrNull()
            val baselineDay = original.days.singleOrNull { it.workDate == date }
            val adjustedDay =
                (previous?.snapshot ?: original).days.singleOrNull { it.workDate == date }
            if (
                baselineDay == null ||
                    adjustedDay == null ||
                    adjustedDay.correctionId != correction?.id
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid")
                )
            val reference =
                WorkforceEmployeeReference(
                    employee.id,
                    employee.employeeNumber,
                    employee.person.legalName,
                    employee.terms.status,
                    employee.version,
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { finalActor
                ->
                AttendanceAdjustmentReview(
                    reference,
                    period,
                    baselineDay,
                    adjustedDay,
                    summarizeAttendance(date, entries, correction),
                    previous?.summary?.revision ?: 0,
                    attendanceAdjustmentRevision(
                        company,
                        reference,
                        period,
                        date,
                        previous?.summary?.revision ?: 0,
                        correction,
                        entries,
                    ),
                    "attendance.correct" in finalActor.permissions &&
                        employee.person.accountId != finalActor.accountId &&
                        current?.person?.accountId != finalActor.accountId,
                    canReviewAttendance(finalActor, current, employee.person.accountId) &&
                        entries
                            .filter { it.status == AttendanceStatus.PENDING }
                            .none { it.accountId == finalActor.accountId },
                )
            }
        }
    }
}
