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

class VerifyPayrollInput(
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
    private val cutoffs: PayrollCutoffRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employee: UUID,
        month: YearMonth,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = actor.requirePermission("payroll.review")
        if (access is Result.Failed) return access
        if (
            month.year !in 2024..2100 ||
                version !in 0..999 ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_input"))
        val key =
            OperationKey(
                "payroll.input_verify",
                operationId,
                listOf(employee.toString(), month.toString(), version.toString(), reason),
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
                    "payroll.review",
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission
            val beneficiary = people.accountForEmployee(company, employee)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "self_payroll_input_review_denied")
                )
            val found = inputs.find(company, employee, month)
            if (found is Result.Failed) return@run found
            val current =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_input_not_found")
                    )
            val authors = inputs.authors(company, current.id)
            if (authors is Result.Failed) return@run authors
            if (actor.accountId in (authors as Result.Success).value)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_input_author_cannot_verify")
                )
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val frozen = cutoffs.frozenMonths(company, employee, setOf(month))
            if (frozen is Result.Failed) return@run frozen
            if ((frozen as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_frozen"))

            if (current.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current.status != PayrollInputStatus.DRAFT)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_input_already_verified")
                )
            if (version == 999L)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_input_revision_limit")
                )
            val employment = people.currentVersion(company, employee)
            if (employment is Result.Failed) return@run employment
            if ((employment as Result.Success).value != current.employmentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_employment_version"))
            val source = (sourceResult as Result.Success).value
            if (source == null || !source.closed)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_workforce_not_closed")
                )
            if (
                !source.includesEmployee ||
                    source.jobId != current.workJobId ||
                    source.version != current.workPeriodVersion
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_workforce_version"))
            val verified =
                current.copy(
                    version = version + 1,
                    status = PayrollInputStatus.VERIFIED,
                    verifiedBy = actor.accountId,
                    recordedAt = clock.instant(),
                    reason = reason,
                )
            inputs.save(company, verified, version).flatMap { receipt ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "payroll_input",
                            current.id,
                            "payroll.input_verified",
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
