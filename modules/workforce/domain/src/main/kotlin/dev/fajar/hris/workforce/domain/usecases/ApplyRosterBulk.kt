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
import java.util.UUID

/** Apply one reviewed, bounded change atomically; no cell may commit on its own. */
class ApplyRosterBulk(
    private val schedules: ScheduleRepository,
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        selection: RosterBulkSelection,
        reviewRevision: String,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("workforce.manage")
        if (permission is Result.Failed) return permission
        val validated = validateRosterBulkSelection(selection)
        if (validated is Result.Failed) return validated
        if (
            !reviewRevision.matches(Regex("[0-9a-f]{64}")) ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_roster_bulk_change"))
        val selected = (validated as Result.Success).value
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "workforce.roster_bulk",
                operationId,
                buildList {
                    add(selected.mode.name)
                    add(selected.shift?.id?.toString())
                    add(selected.shift?.version?.toString())
                    add(reviewRevision)
                    add(reason)
                    add(selected.employeeIds.size.toString())
                    addAll(selected.employeeIds.map(UUID::toString))
                    addAll(selected.dates.map { it.toString() })
                },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val scheduleGuard = schedules.lock(company)
            if (scheduleGuard is Result.Failed) return@run scheduleGuard
            val selectedPeriods = mutableListOf<WorkPeriod>()
            if ((replay as Result.Success).value == null) {
                for (month in selected.dates.map(YearMonth::from).distinct()) {
                    val period = periods.lockMonth(company, month, false)
                    if (period is Result.Failed) return@run period
                    selectedPeriods += (period as Result.Success).value
                }
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
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                    .flatMap { it.requirePermission("workforce.manage") }
            if (authorized is Result.Failed) return@run authorized
            replay.value?.let {
                return@run Result.Success(it)
            }
            for (period in selectedPeriods) {
                val mutable = requireMutablePeriod(period)
                if (mutable is Result.Failed) return@run mutable
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
            val review = rosterBulkReview(company, selected, reviews)
            if (review.revision != reviewRevision)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "roster_bulk_changed"))
            if (review.changeCount == 0)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "roster_bulk_unchanged"))

            for (entry in review.employees) {
                for (day in entry.days.filter { it.changed }) {
                    val saved =
                        schedules.roster(
                            actor,
                            entry.employee.id,
                            day.workDate,
                            shift,
                            day.expectedVersion,
                            reason,
                            active = selected.mode != RosterBulkMode.CALENDAR,
                        )
                    if (saved is Result.Failed) return@run saved
                    val recorded =
                        journal.record(
                            actor,
                            ChangeRecord(
                                "roster_day",
                                entry.employee.id,
                                if (selected.mode == RosterBulkMode.CALENDAR)
                                    "workforce.roster_reset"
                                else "workforce.roster_saved",
                                mapOf(
                                    "workDate" to day.workDate.toString(),
                                    "bulkOperationId" to operationId.toString(),
                                ),
                                reason,
                            ),
                        )
                    if (recorded is Result.Failed) return@run recorded
                }
            }
            val receipt = MutationReceipt(operationId, 0)
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "roster_bulk",
                        operationId,
                        "workforce.roster_bulk_saved",
                        mapOf(
                            "employees" to selected.employeeIds.size.toString(),
                            "changes" to review.changeCount.toString(),
                        ),
                        reason,
                    ),
                )
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap { identities.access(actor.accountId, company) }
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                .flatMap { it.requirePermission("workforce.manage") }
                .map { receipt }
        }
    }
}
