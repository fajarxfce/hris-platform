package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class IssueAttendanceCaptureWindow(
    private val attendance: AttendanceRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val operations: OperationRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        deviceId: UUID,
    ): Result<AttendanceCaptureWindow> {
        val access = actor.requirePermission("attendance.self.record")
        if (access is Result.Failed) return access
        val key =
            OperationKey(
                "attendance.window_issue",
                operationId,
                listOf(employeeId.toString(), deviceId.toString()),
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val windowGuard = attendance.lockWindows(company, employeeId)
            if (windowGuard is Result.Failed) return@run windowGuard
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
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            val permission = live.requirePermission("attendance.self.record")
            if (permission is Result.Failed) return@run permission
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val found = people.find(company, employeeId, today)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.person.accountId != live.accountId)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            (replay as Result.Success).value?.let { receipt ->
                return@run attendance.findWindow(company, receipt.id).flatMap {
                    if (it == null)
                        Result.Failed(Failure(FailureKind.CONFLICT, "capture_window_unavailable"))
                    else Result.Success(it)
                }
            }
            if (!employee.terms.isWorkingOn(today))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val count = attendance.recentWindows(company, employeeId, now.minusSeconds(60))
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 10)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "capture_window_rate_limited")
                )
            val window =
                AttendanceCaptureWindow(
                    UUID.randomUUID(),
                    employeeId,
                    actor.accountId,
                    deviceId,
                    now,
                    now.plusSeconds(120),
                )
            attendance.issueWindow(company, window).flatMap {
                operations.record(actor, key, MutationReceipt(window.id, 0)).map { window }
            }
        }
    }
}
