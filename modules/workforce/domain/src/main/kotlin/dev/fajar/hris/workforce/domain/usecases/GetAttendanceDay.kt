package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.AttendanceRecord
import dev.fajar.hris.workforce.domain.policies.summarizeAttendance
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import java.time.Clock
import java.util.UUID

/** Canonical, account-owned day projection; capture and correction keep their own versions. */
class GetAttendanceDay(
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<AttendanceRecord> {
        val grants = setOf("attendance.self.record", "workforce.read")
        if (actor.permissions.none { it in grants })
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            // Observe only the lock identity before taking the normal day/people/access guards.
            val observed =
                when (val found = attendance.dayHead(company, id)) {
                    is Result.Failed -> return@run found
                    is Result.Success -> found.value
                }
            if (observed != null) {
                val dayGuard =
                    attendance.lockDay(
                        company,
                        observed.employeeId,
                        observed.workDate,
                        shared = true,
                    )
                if (dayGuard is Result.Failed) return@run dayGuard
            }
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
            if (observed == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "attendance_day_not_found"))
            val owner =
                when (val found = people.accountForEmployee(company, observed.employeeId)) {
                    is Result.Failed -> return@run found
                    is Result.Success -> found.value
                }
            // Company-wide read permission never widens this employee-sync representation.
            if (owner != actor.accountId)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "attendance_day_not_found"))
            val head =
                when (val found = attendance.dayHead(company, id)) {
                    is Result.Failed -> return@run found
                    is Result.Success -> found.value
                }
            if (
                head == null ||
                    head.employeeId != observed.employeeId ||
                    head.workDate != observed.workDate
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "attendance_day_not_found"))
            val entries =
                when (
                    val result =
                        attendance.entries(company, head.employeeId, head.workDate, head.workDate)
                ) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            val changes =
                when (
                    val result =
                        corrections.latest(company, head.employeeId, head.workDate, head.workDate)
                ) {
                    is Result.Failed -> return@run result
                    is Result.Success -> result.value
                }
            val record =
                AttendanceRecord(
                    head.id,
                    head.employeeId,
                    head.version,
                    summarizeAttendance(head.workDate, entries, changes.singleOrNull()),
                )
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
                .map { record }
        }
    }
}
