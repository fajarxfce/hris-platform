package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class GetEmployeeAttendance(
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<AttendanceDay>> {
        if (java.time.temporal.ChronoUnit.DAYS.between(from, until) !in 0..30)
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_attendance_range",
                    parameters = mapOf("maximumDays" to "31"),
                )
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            for (offset in 0..java.time.temporal.ChronoUnit.DAYS.between(from, until)) {
                val dayGuard =
                    attendance.lockDay(company, employeeId, from.plusDays(offset), shared = true)
                if (dayGuard is Result.Failed) return@run dayGuard
            }
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            people.find(company, employeeId, until).flatMap { employee ->
                if (employee == null)
                    return@flatMap Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
                people.findAtInstant(company, employeeId, clock.instant()).flatMap { current ->
                    if (
                        !canReadWorkforce(live, employee, current) &&
                            !canReviewAttendance(live, current, employee.person.accountId)
                    )
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
                    else
                        attendance.entries(company, employeeId, from, until).flatMap { entries ->
                            corrections.latest(company, employeeId, from, until).map { changes ->
                                val byDate = entries.groupBy { it.capture.workDate }
                                val correctedDates = changes.associateBy { it.workDate }
                                (0..java.time.temporal.ChronoUnit.DAYS.between(from, until)).map {
                                    day ->
                                    val date = from.plusDays(day)
                                    summarizeAttendance(
                                        date,
                                        byDate[date].orEmpty(),
                                        correctedDates[date],
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}
