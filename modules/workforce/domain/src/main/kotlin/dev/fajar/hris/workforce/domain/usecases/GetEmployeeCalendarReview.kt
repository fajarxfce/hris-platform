package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.EmployeeCalendarReview
import dev.fajar.hris.workforce.domain.entities.WorkforceEmployeeReference
import dev.fajar.hris.workforce.domain.policies.effectiveScheduleAssignment
import dev.fajar.hris.workforce.domain.policies.resolveCalendar
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class GetEmployeeCalendarReview(
    private val schedules: ScheduleRepository,
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
        from: LocalDate,
        until: LocalDate,
    ): Result<EmployeeCalendarReview> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        if (ChronoUnit.DAYS.between(from, until) !in 0..61)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_calendar_range"))
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
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (authorized is Result.Failed) return@run authorized
            val permission =
                (authorized as Result.Success).value.requirePermission("workforce.read")
            if (permission is Result.Failed) return@run permission
            people.find(company, employeeId, until).flatMap { employee ->
                if (employee == null)
                    return@flatMap Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
                val reference =
                    WorkforceEmployeeReference(
                        employee.id,
                        employee.employeeNumber,
                        employee.person.legalName,
                        employee.terms.status,
                        employee.version,
                    )
                schedules.calendar(company, employeeId, from, until).flatMap { facts ->
                    resolveCalendar(facts).map { calendar ->
                        EmployeeCalendarReview(
                            reference,
                            from,
                            until,
                            calendar,
                            effectiveScheduleAssignment(facts.assignments, from),
                        )
                    }
                }
            }
        }
    }
}
