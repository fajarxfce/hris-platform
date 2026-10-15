package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetOffboardingScheduleReview(
    private val schedules: OffboardingScheduleRepository,
    private val lifecycle: LifecycleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, caseId: UUID): Result<OffboardingScheduleReview> {
        val permission = requireOffboardingAccess(actor, reading = true)
        if (permission is Result.Failed) return permission
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val caseGuard = lifecycle.lockCase(company, caseId, shared = true)
            if (caseGuard is Result.Failed) return@run caseGuard
            val found = lifecycle.caseDetails(company, caseId)
            if (found is Result.Failed) return@run found
            val details = (found as Result.Success).value
            val binding =
                if (details == null) Result.Success(null)
                else people.accountForEmployee(company, details.case.employmentId)
            if (binding is Result.Failed) return@run binding
            val targetAccount = (binding as Result.Success).value
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            for (id in listOfNotNull(actor.accountId, targetAccount).distinct().sorted()) {
                val guard = identities.lockAccount(id, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val access = (loadedAccess as Result.Success).value
            val checked =
                validateOffboardingSession(actor, access, clock.instant(), security, reading = true)
            if (checked is Result.Failed) return@run checked
            if (details == null)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "lifecycle_case_not_found"))
            val case = details.case
            if (case.kind != LifecycleKind.OFFBOARDING || case.status != LifecycleStatus.OPEN)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open"))
            val loadedCompany = companies.find(company)
            if (loadedCompany is Result.Failed) return@run loadedCompany
            val timezone =
                (loadedCompany as Result.Success).value?.timezone
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val window = offboardingWindow(case.targetDate, timezone, clock.instant())
            val loadedEmployee = people.find(company, case.employmentId, window.today)
            if (loadedEmployee is Result.Failed) return@run loadedEmployee
            val employee =
                (loadedEmployee as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val loadedAttempts = schedules.forCase(company, caseId)
            if (loadedAttempts is Result.Failed) return@run loadedAttempts
            val attempts = (loadedAttempts as Result.Success).value
            val blockers = offboardingBasisBlockers(case, employee, actor.accountId).toMutableList()
            blockers += offboardingScheduleBlockers(case, window, clock.instant(), attempts)
            val otherCases = lifecycle.hasOpenCases(company, employee.id, caseId)
            if (otherCases is Result.Failed) return@run otherCases
            if ((otherCases as Result.Success).value) blockers += "other_lifecycle_cases_pending"
            val futureChanges = people.hasRevisionsAfter(company, employee.id, window.today)
            if (futureChanges is Result.Failed) return@run futureChanges
            if ((futureChanges as Result.Success).value)
                blockers += "scheduled_employment_changes_pending"
            val executeOn = case.targetDate.plusDays(1)
            val reports = people.hasReportingDependentsAtOrAfter(company, employee.id, executeOn)
            if (reports is Result.Failed) return@run reports
            if ((reports as Result.Success).value) blockers += "reporting_reassignment_required"
            if (targetAccount != null) {
                val remaining =
                    people.hasOpenEmploymentAtOrAfter(
                        company,
                        employee.person.id,
                        employee.id,
                        executeOn,
                    )
                if (remaining is Result.Failed) return@run remaining
                val membership = members.find(company, targetAccount)
                if (membership is Result.Failed) return@run membership
                val member = (membership as Result.Success).value
                if (
                    !(remaining as Result.Success).value &&
                        member?.membershipActive == true &&
                        "identity.manage" in member.permissions
                ) {
                    val other =
                        members.hasOtherActiveMember(company, targetAccount, "identity.manage")
                    if (other is Result.Failed) return@run other
                    if (!(other as Result.Success).value) blockers += "last_company_administrator"
                }
            }
            validateOffboardingSession(actor, access, clock.instant(), security, reading = true)
                .map {
                    OffboardingScheduleReview(
                        details,
                        employee.version,
                        employee.appliedRevision,
                        window,
                        offboardingBasisRevision(case, employee, window),
                        attempts.size,
                        blockers.toList(),
                    )
                }
        }
    }
}
