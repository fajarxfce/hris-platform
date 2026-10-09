package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.canReadWorkforce
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.*
import java.util.UUID

class GetWorkPeriodSnapshot(
    private val periods: WorkPeriodRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, month: YearMonth, employeeId: UUID): Result<WorkPeriodSnapshot> {
        if (month.year !in 2000..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val employee = people.find(company, employeeId, month.atEndOfMonth())
            if (employee is Result.Failed) return@run employee
            val historical =
                (employee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val current = people.findAtInstant(company, employeeId, clock.instant())
            if (current is Result.Failed) return@run current
            if (!canReadWorkforce(live, historical, (current as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val found = periods.find(company, month)
            if (found is Result.Failed) return@run found
            val period = (found as Result.Success).value
            val jobId = period?.jobId
            if (period?.status != WorkPeriodStatus.CLOSED || jobId == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "work_period_snapshot_unavailable")
                )
            periods.snapshot(company, jobId, employeeId).flatMap { snapshot ->
                snapshot?.let { Result.Success(it) }
                    ?: Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "work_period_snapshot_unavailable")
                    )
            }
        }
    }
}
