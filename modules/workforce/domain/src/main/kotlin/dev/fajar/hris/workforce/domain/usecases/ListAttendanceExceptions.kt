package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.parseAttendanceExceptionCursor
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ListAttendanceExceptions(
    private val attendance: AttendanceRepository,
    private val people: PeopleRepository,
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
        kind: AttendanceExceptionKind?,
        after: String?,
        limit: Int,
    ): Result<Page<AttendanceException>> {
        val access = actor.requirePermission("workforce.read")
        if (access is Result.Failed) return access
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            from.year !in 1..9999 ||
                until.year !in 1..9999 ||
                ChronoUnit.DAYS.between(from, until) !in 0..30
        )
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_attendance_range",
                    parameters = mapOf("maximumDays" to "31"),
                )
            )
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val cursor = parseAttendanceExceptionCursor(company, from, until, kind, after)
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
            val authorized = validateCompanySessionActor(actor, snapshot, clock.instant(), security)
            if (authorized is Result.Failed) return@run authorized
            val permission =
                (authorized as Result.Success).value.requirePermission("workforce.read")
            if (permission is Result.Failed) return@run permission
            attendance
                .exceptions(company, from, until, kind, (cursor as Result.Success).value, limit)
                .flatMap { page ->
                    validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                        page
                    }
                }
        }
    }
}
