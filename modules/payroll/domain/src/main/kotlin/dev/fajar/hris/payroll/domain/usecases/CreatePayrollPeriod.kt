package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class CreatePayrollPeriod(
    private val periods: PayrollPeriodRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        month: YearMonth,
        paymentDate: LocalDate,
        employeeIds: Set<UUID>,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        val valid = validatePayrollPeriod(month, paymentDate, employeeIds, reason)
        if (valid is Result.Failed) return valid
        val employees = employeeIds.sortedBy(UUID::toString)
        val key =
            OperationKey(
                "payroll.period_create",
                operationId,
                listOf(id.toString(), month.toString(), paymentDate.toString(), reason) +
                    employees.map(UUID::toString),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val permission =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.calculate",
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = periods.find(company, id)
            if (found is Result.Failed) return@run found
            if ((found as Result.Success).value != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_exists"))
            val active = periods.active(company, month)
            if (active is Result.Failed) return@run active
            if ((active as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_active"))
            val count = periods.count(company, month)
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 20)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_period_attempt_limit")
                )
            val available = people.existingEmployeeIds(company, employeeIds)
            if (available is Result.Failed) return@run available
            if ((available as Result.Success).value != employeeIds)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "payroll_period_employee_unavailable")
                )
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val companySettings =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val period =
                PayrollPeriod(
                    id,
                    month,
                    paymentDate,
                    companySettings.timezone,
                    employees.size,
                    actor.accountId,
                    clock.instant(),
                    PayrollPeriodStatus.DRAFT,
                    0,
                )
            periods.create(company, period, employees, reason).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_period",
                            id,
                            "payroll.period_created",
                            mapOf(
                                "earningsMonth" to month.toString(),
                                "plannedPaymentMonth" to period.plannedPaymentMonth.toString(),
                                "participants" to employees.size.toString(),
                            ),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
