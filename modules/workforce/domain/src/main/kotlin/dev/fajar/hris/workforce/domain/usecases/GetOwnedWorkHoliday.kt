package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.WorkHoliday
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock
import java.util.UUID

/** Canonical input for the employee's local calendar; never widens to a team export. */
class GetOwnedWorkHoliday(
    private val schedules: ScheduleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<WorkHoliday> {
        val grants = setOf("attendance.self.record", "workforce.read")
        if (actor.permissions.none { it in grants })
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
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            if (live.permissions.none { it in grants })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val record =
                when (val result = schedules.findHoliday(company, id)) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                } ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "holiday_not_found"))
            val ownership = people.employeeIdsForAccount(company, actor.accountId, 1)
            if (ownership is Result.Failed) return@run ownership
            if ((ownership as Result.Success).value.isEmpty())
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "holiday_not_found"))
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                .map { record }
        }
    }
}
