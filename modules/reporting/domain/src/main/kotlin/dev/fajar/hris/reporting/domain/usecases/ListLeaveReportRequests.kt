package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.LeaveStatus
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.LeaveReportRequestPage
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.LeaveReportRepository
import java.time.Clock
import java.time.LocalDate

class ListLeaveReportRequests(
    private val reports: LeaveReportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        from: LocalDate,
        until: LocalDate,
        status: LeaveStatus?,
        after: String?,
        limit: Int,
    ): Result<LeaveReportRequestPage> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateLeaveReportAccess(actor)
        if (access is Result.Failed) return access
        val range = validateLeaveReportRange(from, until)
        if (range is Result.Failed) return range
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val cursor = parseLeaveReportCursor(company, from, until, status, after)
        if (cursor is Result.Failed) return cursor
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val current = identities.access(actor.accountId, company)
            if (current is Result.Failed) return@run current
            val snapshot = (current as Result.Success).value
            val admitted =
                validateCompanySessionActor(actor, snapshot, clock.instant(), security)
                    .flatMap(::validateLeaveReportAccess)
            if (admitted is Result.Failed) return@run admitted
            val evaluatedAt = clock.instant()
            reports
                .requests(company, from, until, status, (cursor as Result.Success).value, limit)
                .flatMap { page ->
                    validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                        LeaveReportRequestPage(company, from, until, status, evaluatedAt, page)
                    }
                }
        }
    }
}
