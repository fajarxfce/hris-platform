package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class GetLeaveAccrualSchedule(
    private val schedules: LeaveAccrualScheduleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<LeaveAccrualScheduleDetails> {
        val access = actor.requirePermission("leave.read")
        if (access is Result.Failed) return access
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = live.requirePermission("leave.read")
            if (allowed is Result.Failed) return@run allowed
            val acquired = schedules.find(company, id)
            if (acquired is Result.Failed) return@run acquired
            val schedule =
                (acquired as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_accrual_schedule_not_found")
                    )
            val periods = schedules.periods(company, id)
            if (periods is Result.Failed) return@run periods
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                LeaveAccrualScheduleDetails(schedule, (periods as Result.Success).value)
            }
        }
    }
}
