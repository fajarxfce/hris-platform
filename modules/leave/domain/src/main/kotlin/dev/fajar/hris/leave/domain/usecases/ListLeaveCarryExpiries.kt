package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.LeaveCarryExpiryOverview
import dev.fajar.hris.leave.domain.repositories.LeaveCarryExpiryRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class ListLeaveCarryExpiries(
    private val expiries: LeaveCarryExpiryRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<LeaveCarryExpiryOverview>> {
        val permission = actor.requirePermission("leave.read")
        if (permission is Result.Failed) return permission
        if (limit !in 1..100) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val allowed = (checked as Result.Success).value.requirePermission("leave.read")
            if (allowed is Result.Failed) return@run allowed
            val acquired = expiries.overviews(company, after, limit)
            if (acquired is Result.Failed) return@run acquired
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                (acquired as Result.Success).value
            }
        }
    }
}
