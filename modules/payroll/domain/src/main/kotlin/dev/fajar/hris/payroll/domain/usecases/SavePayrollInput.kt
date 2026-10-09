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

class SavePayrollInput(
    private val inputs: PayrollInputRepository,
    private val sources: PayrollWorkSourceRepository,
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
        employee: UUID,
        month: YearMonth,
        workJob: UUID,
        workPeriodVersion: Long,
        employmentVersion: Long,
        terms: PayrollInputTerms,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.calculate")
        if (access is Result.Failed) return access
        val valid = validatePayrollInput(month, terms, reason)
        if (valid is Result.Failed) return valid
        if (
            (expectedVersion != null && expectedVersion !in 0..999) ||
                workPeriodVersion < 0 ||
                employmentVersion < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.input_save",
                operationId,
                listOf(
                    employee.toString(),
                    month.toString(),
                    workJob.toString(),
                    workPeriodVersion.toString(),
                    employmentVersion.toString(),
                    expectedVersion?.toString(),
                    reason,
                ) + payrollInputParts(terms),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyLock = policies.lock(company)
            if (policyLock is Result.Failed) return@run policyLock
            val sourceResult = sources.find(company, employee, month, lock = true)
            if (sourceResult is Result.Failed) return@run sourceResult
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
            val beneficiary = people.accountForEmployee(company, employee)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "self_payroll_input_change_denied")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val employment = people.currentVersion(company, employee)
            if (employment is Result.Failed) return@run employment
            val currentEmployment =
                (employment as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (currentEmployment != employmentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val source = (sourceResult as Result.Success).value
            if (source == null || !source.closed)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_workforce_not_closed")
                )
            if (!source.includesEmployee)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_workforce_employee_missing")
                )
            if (source.jobId != workJob || source.version != workPeriodVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_workforce_version"))
            val found = inputs.find(company, employee, month)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current?.version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_input_revision_limit")
                )
            val input =
                PayrollInput(
                    current?.id ?: UUID.randomUUID(),
                    employee,
                    month,
                    (expectedVersion ?: -1) + 1,
                    workJob,
                    workPeriodVersion,
                    employmentVersion,
                    terms,
                    PayrollInputStatus.DRAFT,
                    actor.accountId,
                    null,
                    clock.instant(),
                    reason,
                )
            inputs.save(company, input, expectedVersion).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_input",
                            input.id,
                            "payroll.input_saved",
                            mapOf(
                                "employeeId" to employee.toString(),
                                "earningsMonth" to month.toString(),
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
