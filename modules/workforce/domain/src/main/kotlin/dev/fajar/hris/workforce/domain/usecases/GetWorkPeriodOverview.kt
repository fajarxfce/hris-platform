package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.WorkPeriodOverview
import dev.fajar.hris.workforce.domain.policies.availableWorkPeriodActions
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.*

class GetWorkPeriodOverview(
    private val periods: WorkPeriodRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, month: YearMonth): Result<WorkPeriodOverview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (actor.permissions.none { it in setOf("workforce.read", "workforce.close") })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (month.year !in 2000..2100)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_work_period_range"))
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val current = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, current, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (live.permissions.none { it in setOf("workforce.read", "workforce.close") })
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            periods.state(company, month).flatMap { state ->
                validateCompanySessionActor(actor, current, clock.instant(), security).map {
                    val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(settings.timezone))
                    WorkPeriodOverview(
                        month,
                        today,
                        settings.timezone,
                        state,
                        availableWorkPeriodActions(live, month, state, today),
                    )
                }
            }
        }
    }
}
