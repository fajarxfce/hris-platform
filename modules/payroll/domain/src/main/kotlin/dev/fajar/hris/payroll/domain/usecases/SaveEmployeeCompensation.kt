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

class SaveEmployeeCompensation(
    private val policies: PayrollPolicyRepository,
    private val compensations: CompensationRepository,
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
        employeeId: UUID,
        effectiveFrom: YearMonth,
        terms: CompensationTerms,
        expectedVersion: Long?,
        expectedEmploymentVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.compensation.manage")
        if (access is Result.Failed) return access
        val valid = validateCompensation(terms, effectiveFrom, reason)
        if (valid is Result.Failed) return valid
        if (
            (expectedVersion != null && expectedVersion !in 0..999) || expectedEmploymentVersion < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.compensation_save",
                operationId,
                listOf(
                    employeeId.toString(),
                    effectiveFrom.toString(),
                    expectedVersion?.toString(),
                    expectedEmploymentVersion.toString(),
                    reason,
                ) + compensationOperationParts(terms),
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
            val live = (checked as Result.Success).value

            val permission =
                requirePayrollMutation(
                    live,
                    "payroll.compensation.manage",
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission
            val beneficiary = people.accountForEmployee(company, employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "self_compensation_change_denied")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = clock.instant().atZone(ZoneId.of(settings.timezone)).toLocalDate()
            if (terms.tax.verifiedOn > today)
                return@run Result.Failed(
                    Failure(
                        FailureKind.VALIDATION,
                        "invalid_compensation",
                        fields = mapOf("terms.tax.verifiedOn" to "future"),
                    )
                )
            val employeeVersion = people.currentVersion(company, employeeId)
            if (employeeVersion is Result.Failed) return@run employeeVersion
            val currentEmployeeVersion =
                (employeeVersion as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (currentEmployeeVersion != expectedEmploymentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val employeeResult = people.find(company, employeeId, effectiveFrom.atEndOfMonth())
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val found = compensations.current(company, employeeId)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current?.version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "compensation_revision_limit")
                )
            val compensation =
                EmployeeCompensation(
                    employeeId,
                    employee.employeeNumber,
                    employee.person.legalName,
                    expectedVersion ?: 0,
                    expectedVersion ?: 0,
                    effectiveFrom,
                    terms,
                )
            compensations.save(actor, compensation, expectedVersion, reason).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "employee_compensation",
                            employeeId,
                            "payroll.compensation_saved",
                            reason = reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
