package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.workforce.domain.entities.ShiftDefinition
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.Clock

class ListShifts(
    private val schedules: ScheduleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        query: String,
        after: String?,
        limit: Int,
    ): Result<Page<ShiftDefinition>> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        if (query.length > 120 || (after?.length ?: 0) > 32 || limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val scheduleGuard = schedules.lock(company, shared = true)
            if (scheduleGuard is Result.Failed) return@run scheduleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val accountAccess = (authority as Result.Success).value
            val authorized =
                validateCompanySessionActor(actor, accountAccess, clock.instant(), security)
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            val permission = live.requirePermission("workforce.read")
            if (permission is Result.Failed) return@run permission
            schedules.shifts(company, query.trim(), after, limit).flatMap { value ->
                validateCompanySessionActor(actor, accountAccess, clock.instant(), security).map {
                    value
                }
            }
        }
    }
}
