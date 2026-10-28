package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.reporting.domain.entities.AttendanceClosingEmployeePage
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import java.time.Clock
import java.time.YearMonth

class ListAttendanceClosingEmployees(
    private val reports: AttendanceClosingReportRepository,
    private val companies: CompanyRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        month: YearMonth,
        after: String?,
        limit: Int,
    ): Result<AttendanceClosingEmployeePage> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateAttendanceReportAccess(actor)
        if (access is Result.Failed) return access
        val range = validateAttendanceClosingReportRange(month, month)
        if (range is Result.Failed) return range
        if (limit !in 1..50) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val cursor = parseAttendanceClosingEmployeeCursor(company, month, after)
        if (cursor is Result.Failed) return cursor
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
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
                    .flatMap(::validateAttendanceReportAccess)
            if (admitted is Result.Failed) return@run admitted
            val evaluatedAt = clock.instant()
            reports.employees(company, month, (cursor as Result.Success).value, limit).flatMap {
                page ->
                validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                    AttendanceClosingEmployeePage(company, month, evaluatedAt, page)
                }
            }
        }
    }
}
