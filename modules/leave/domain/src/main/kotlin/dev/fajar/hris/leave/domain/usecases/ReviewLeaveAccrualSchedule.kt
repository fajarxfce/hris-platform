package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class ReviewLeaveAccrualSchedule(
    private val batches: LeaveBatchRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        typeId: UUID,
        firstPeriod: YearMonth,
        periods: Int,
    ): Result<LeaveAccrualScheduleReview> {
        val initial = actor.requirePermission(LeaveBatchKind.ACCRUAL.permission)
        if (initial is Result.Failed) return initial
        val valid = validateLeaveAccrualScheduleSelection(firstPeriod, periods)
        if (valid is Result.Failed) return valid
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
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
            val permission =
                (checked as Result.Success)
                    .value
                    .requirePermission(LeaveBatchKind.ACCRUAL.permission)
            if (permission is Result.Failed) return@run permission
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val firstResult = policies.effective(company, typeId, firstPeriod.atDay(1))
            if (firstResult is Result.Failed) return@run firstResult
            val first =
                (firstResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            val frequency =
                first.policy.accrual?.frequency
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_accrual_not_configured")
                    )
            val monthsResult = leaveAccrualScheduleMonths(firstPeriod, periods, frequency)
            if (monthsResult is Result.Failed) return@run monthsResult
            val definitions = mutableListOf<LeaveAccrualPeriodReview>()
            for (month in (monthsResult as Result.Success).value) {
                val found =
                    if (month == firstPeriod) Result.Success(first)
                    else policies.effective(company, typeId, month.atDay(1))
                if (found is Result.Failed) return@run found
                val type =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                        )
                val validPolicy = validateScheduledLeavePolicy(type, frequency)
                if (validPolicy is Result.Failed) return@run validPolicy
                val active = batches.active(company, typeId, LeaveBatchKind.ACCRUAL, month)
                if (active is Result.Failed) return@run active
                if ((active as Result.Success).value)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_active"))
                definitions +=
                    LeaveAccrualPeriodReview(
                        month,
                        leaveAccrualDueAt(month, frequency, settings.timezone),
                        type,
                    )
            }

            val employees = people.employeeIds(company, 5001)
            if (employees is Result.Failed) return@run employees
            val ids = (employees as Result.Success).value.sortedBy { it.toString() }
            if (ids.size !in 1..5000)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_batch_employee_limit")
                )
            val review =
                LeaveAccrualScheduleReview(
                    LocalDate.ofInstant(clock.instant(), ZoneId.of(settings.timezone)),
                    settings.timezone,
                    frequency,
                    first.version,
                    java.util.List.copyOf(definitions),
                    java.util.List.copyOf(ids),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { review }
        }
    }
}
