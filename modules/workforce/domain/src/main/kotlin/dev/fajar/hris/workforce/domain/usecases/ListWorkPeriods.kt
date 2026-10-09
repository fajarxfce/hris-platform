package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.WorkPeriod
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class ListWorkPeriods(
    private val periods: WorkPeriodRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, from: YearMonth, until: YearMonth): Result<List<WorkPeriod>> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        if (
            from.year !in 2000..2100 ||
                until.year !in 2000..2100 ||
                ChronoUnit.MONTHS.between(from, until) !in 0..23
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
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
            val permission = live.requirePermission("workforce.read")
            if (permission is Result.Failed) return@run permission
            periods.list(company, from, until)
        }
    }
}
