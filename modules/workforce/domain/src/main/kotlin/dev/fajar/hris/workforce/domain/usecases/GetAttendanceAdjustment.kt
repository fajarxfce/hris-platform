package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.canReadWorkforce
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetAttendanceAdjustment(
    private val periods: WorkPeriodRepository,
    private val adjustments: AttendanceAdjustmentRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, employeeId: UUID, id: UUID): Result<AttendanceAdjustmentDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
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
            val found = adjustments.find(company, id)
            if (found is Result.Failed) return@run found
            val adjustment = (found as Result.Success).value
            if (adjustment == null || adjustment.snapshot.employeeId != employeeId)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "attendance_adjustment_not_found")
                )
            val employeeResult = people.find(company, employeeId, adjustment.summary.workDate)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val current = people.findAtInstant(company, employeeId, clock.instant())
            if (current is Result.Failed) return@run current
            if (
                !canReadWorkforce(
                    (checked as Result.Success).value,
                    employee,
                    (current as Result.Success).value,
                )
            )
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "attendance_adjustment_not_found")
                )
            val periodResult = periods.find(company, adjustment.snapshot.month)
            if (periodResult is Result.Failed) return@run periodResult
            val period = (periodResult as Result.Success).value
            if (
                period?.status != WorkPeriodStatus.CLOSED || period.jobId != adjustment.closingJobId
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid")
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map {
                AttendanceAdjustmentDetails(
                    WorkforceEmployeeReference(
                        employee.id,
                        employee.employeeNumber,
                        employee.person.legalName,
                        employee.terms.status,
                        employee.version,
                    ),
                    period,
                    adjustment,
                )
            }
        }
    }
}
