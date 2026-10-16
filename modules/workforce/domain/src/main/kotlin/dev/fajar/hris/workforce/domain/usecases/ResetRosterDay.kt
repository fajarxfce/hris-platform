package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Withdraws a reviewed override while preserving its version and original evidence. */
class ResetRosterDay(
    private val periods: WorkPeriodRepository,
    private val schedules: ScheduleRepository,
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
        employeeId: UUID,
        date: LocalDate,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission("workforce.manage")
        if (permission is Result.Failed) return permission
        if (
            version !in 0 until Long.MAX_VALUE ||
                date.year !in 1900..9999 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_roster_change"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val key =
            OperationKey(
                "workforce.roster_reset",
                operationId,
                listOf(employeeId.toString(), date.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val calendarGuard = schedules.lock(company)
            if (calendarGuard is Result.Failed) return@run calendarGuard
            val period =
                if ((replay as Result.Success).value == null)
                    periods.lockMonth(company, YearMonth.from(date), false)
                else Result.Success(null)
            if (period is Result.Failed) return@run period
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
            val mutable = requireMutablePeriod(requireNotNull((period as Result.Success).value))
            if (mutable is Result.Failed) return@run mutable
            val employee = people.find(company, employeeId, date)
            if (employee is Result.Failed) return@run employee
            if ((employee as Result.Success).value?.terms?.isWorkingOn(date) != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            val loaded = schedules.calendar(company, employeeId, date, date)
            if (loaded is Result.Failed) return@run loaded
            val facts = (loaded as Result.Success).value
            val current = facts.roster.singleOrNull { it.workDate == date }
            if (current == null || current.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (!current.active)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "roster_override_required"))
            // Check the restored rule's local time before withdrawing the override.
            val fallback = resolveCalendar(facts.copy(roster = emptyList()))
            if (fallback is Result.Failed) return@run fallback
            schedules
                .roster(actor, employeeId, date, null, version, reason, active = false)
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "roster_day",
                                employeeId,
                                "workforce.roster_reset",
                                mapOf("workDate" to date.toString()),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            identities
                                .access(actor.accountId, company)
                                .flatMap {
                                    validateCompanySessionActor(
                                        actor,
                                        it,
                                        clock.instant(),
                                        security,
                                    )
                                }
                                .flatMap { it.requirePermission("workforce.manage") }
                        }
                        .map { receipt }
                }
        }
    }
}
