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

class ReviewLeaveBatch(
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
        kind: LeaveBatchKind,
        typeId: UUID,
        period: YearMonth,
    ): Result<LeaveBatchReview> {
        val initial = actor.requirePermission(kind.permission)
        if (initial is Result.Failed) return initial
        val valid = validateLeaveBatchPeriod(kind, period)
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
            val admitted = (checked as Result.Success).value.requirePermission(kind.permission)
            if (admitted is Result.Failed) return@run admitted
            val settingsResult = companies.find(company)
            if (settingsResult is Result.Failed) return@run settingsResult
            val settings =
                (settingsResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(settings.timezone))
            val effectiveOn =
                if (kind == LeaveBatchKind.ACCRUAL) period.atDay(1) else period.atEndOfMonth()
            val found = policies.effective(company, typeId, effectiveOn)
            if (found is Result.Failed) return@run found
            val type =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "leave_type_unavailable")
                    )
            val policy = validateLeaveBatchPolicy(kind, period, today, type)
            if (policy is Result.Failed) return@run policy
            val active = batches.active(company, typeId, kind, period)
            if (active is Result.Failed) return@run active
            if ((active as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "leave_batch_active"))
            val selected = people.employeeIds(company, 5001)
            if (selected is Result.Failed) return@run selected
            val ids = (selected as Result.Success).value.sortedBy { it.toString() }
            val count = ids.size
            if (count !in 1..5000)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "leave_batch_employee_limit")
                )
            val review =
                LeaveBatchReview(
                    kind,
                    period,
                    today,
                    settings.timezone,
                    type,
                    count,
                    java.util.List.copyOf(ids),
                )
            validateCompanySessionActor(actor, access, clock.instant(), security).map { review }
        }
    }
}
