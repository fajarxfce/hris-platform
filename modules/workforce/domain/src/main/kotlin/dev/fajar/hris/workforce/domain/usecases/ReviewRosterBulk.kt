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
import java.time.YearMonth

class ReviewRosterBulk(
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
    fun execute(actor: Actor, selection: RosterBulkSelection): Result<RosterBulkReview> {
        val permission = actor.requirePermission("workforce.read")
        if (permission is Result.Failed) return permission
        val validated = validateRosterBulkSelection(selection)
        if (validated is Result.Failed) return validated
        val selected = (validated as Result.Success).value
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
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("workforce.read") }
            if (authorized is Result.Failed) return@run authorized

            // Review creates no period rows and reserves no future write authority.
            for (month in selected.dates.map(YearMonth::from).distinct()) {
                val period = periods.find(company, month)
                if (period is Result.Failed) return@run period
                (period as Result.Success).value?.let {
                    val mutable = requireMutablePeriod(it)
                    if (mutable is Result.Failed) return@run mutable
                }
            }
            val definition =
                selected.shift?.let { schedules.findShift(company, it.id) } ?: Result.Success(null)
            if (definition is Result.Failed) return@run definition
            val snapshot = rosterBulkShift(selected, (definition as Result.Success).value)
            if (snapshot is Result.Failed) return@run snapshot
            val shift = (snapshot as Result.Success).value
            val from = selected.dates.first()
            val until = selected.dates.last()
            val reviews = mutableListOf<RosterBulkEmployee>()
            for (employeeId in selected.employeeIds) {
                val acquired = people.find(company, employeeId, until)
                if (acquired is Result.Failed) return@run acquired
                val employee =
                    (acquired as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "employee_not_found")
                        )
                val history = people.effectiveRevisions(company, employeeId, from, until)
                if (history is Result.Failed) return@run history
                val facts = schedules.calendar(company, employeeId, from, until)
                if (facts is Result.Failed) return@run facts
                val reviewed =
                    reviewRosterBulkEmployee(
                        selected,
                        employee,
                        (history as Result.Success).value,
                        (facts as Result.Success).value,
                        shift,
                    )
                if (reviewed is Result.Failed) return@run reviewed
                reviews += (reviewed as Result.Success).value
            }
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                .flatMap { it.requirePermission("workforce.read") }
                .map { rosterBulkReview(company, selected, reviews) }
        }
    }
}
