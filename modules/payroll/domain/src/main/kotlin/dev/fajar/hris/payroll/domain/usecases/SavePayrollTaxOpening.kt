package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class SavePayrollTaxOpening(
    private val openings: PayrollTaxOpeningRepository,
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
    private val cutoffs: PayrollCutoffRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employee: UUID,
        year: Int,
        terms: PayrollTaxOpeningTerms,
        expectedVersion: Long?,
        expectedEmploymentVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("payroll.calculate")
        if (permission is Result.Failed) return permission
        val valid = validatePayrollTaxOpening(year, terms, reason)
        if (valid is Result.Failed) return valid
        if (
            (expectedVersion != null && expectedVersion !in 0..999) || expectedEmploymentVersion < 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val key =
            OperationKey(
                "payroll.tax_opening_save",
                operationId,
                listOf(
                    employee.toString(),
                    year.toString(),
                    expectedVersion?.toString(),
                    expectedEmploymentVersion.toString(),
                    reason,
                ) + payrollTaxOpeningParts(terms),
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
            val allowed =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.calculate",
                    clock.instant(),
                    security,
                )
            if (allowed is Result.Failed) return@run allowed
            val beneficiary = people.accountForEmployee(company, employee)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "self_payroll_input_change_denied")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val frozen =
                cutoffs.frozenMonths(
                    company,
                    employee,
                    (1..12).map { java.time.YearMonth.of(year, it) }.toSet(),
                )
            if (frozen is Result.Failed) return@run frozen
            if ((frozen as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_frozen"))

            val employment = people.currentVersion(company, employee)
            if (employment is Result.Failed) return@run employment
            val employmentVersion =
                (employment as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employmentVersion != expectedEmploymentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val found = openings.find(company, employee, year)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (current?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current?.version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_tax_opening_revision_limit")
                )
            val settings = companies.find(company)
            if (settings is Result.Failed) return@run settings
            val companySettings =
                (settings as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = clock.instant().atZone(ZoneId.of(companySettings.timezone)).toLocalDate()
            if (
                terms.throughMonth > 0 &&
                    YearMonth.of(year, terms.throughMonth).atEndOfMonth() > today
            )
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "payroll_tax_opening_future")
                )
            val opening =
                PayrollTaxOpening(
                    current?.id ?: UUID.randomUUID(),
                    employee,
                    year,
                    (expectedVersion ?: -1) + 1,
                    terms,
                    PayrollTaxOpeningStatus.DRAFT,
                    actor.accountId,
                    null,
                    clock.instant(),
                    reason,
                )
            openings.save(company, opening, expectedVersion).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_tax_opening",
                            opening.id,
                            "payroll.tax_opening_saved",
                            mapOf("employeeId" to employee.toString(), "year" to year.toString()),
                            reason,
                        ),
                    )
                    .flatMap { operations.record(actor, key, receipt) }
                    .map { receipt }
            }
        }
    }
}
