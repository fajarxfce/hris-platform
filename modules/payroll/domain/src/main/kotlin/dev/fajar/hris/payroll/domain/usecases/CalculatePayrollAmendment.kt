package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class CalculatePayrollAmendment(
    private val amendments: PayrollAmendmentRepository,
    private val sources: PayrollAmendmentSourceRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentCommand(expectedVersion, reason)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "payroll.amendment_calculate",
                operationId,
                listOf(id.toString(), expectedVersion.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val workGuard = sources.lockAmendmentWorkPeriods(company, id)
            if (workGuard is Result.Failed) return@run workGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val allowed =
                requirePayrollMutation(
                    (checked as Result.Success).value,
                    "payroll.amend",
                    clock.instant(),
                    security,
                )
            if (allowed is Result.Failed) return@run allowed
            val found = amendments.find(company, id)
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            if (current == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                )
            val beneficiary = people.accountForEmployee(company, current.summary.employee.id)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            (replay as Result.Success).value?.let { receipt ->
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
            if (current.summary.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (current.summary.status != PayrollAmendmentStatus.DRAFT)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_not_editable")
                )
            if (current.summary.version >= PAYROLL_AMENDMENT_MAXIMUM_VERSION - 1)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_revision_limit")
                )
            val previous =
                current.summary.latestCalculationId?.let { calculationId ->
                    val result = amendments.calculationSummary(company, id, calculationId)
                    if (result is Result.Failed) return@run result
                    (result as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.UNEXPECTED, "payroll_amendment_calculation_missing")
                        )
                }
            if ((previous?.number ?: 0) >= PAYROLL_AMENDMENT_MAXIMUM_CALCULATIONS)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_calculation_limit")
                )
            val acquired = sources.sources(company, current.summary.firstAssessmentId)
            if (acquired is Result.Failed) return@run acquired
            val year = (acquired as Result.Success).value
            val acquiredAttendance =
                sources.attendance(
                    company,
                    payrollAmendmentAttendanceReferences(current.terms.corrections),
                )
            if (acquiredAttendance is Result.Failed) return@run acquiredAttendance
            val attendance = (acquiredAttendance as Result.Success).value
            val corrections =
                preparePayrollAmendmentCorrections(
                    current.summary.firstAssessmentId,
                    year,
                    current.terms.sourceVersions,
                    current.terms.corrections,
                    attendance,
                )
            if (corrections is Result.Failed) return@run corrections
            val currentAttendance = requireCurrentPayrollAmendmentAttendance(attendance)
            if (currentAttendance is Result.Failed) return@run currentAttendance
            // Only a completed pure rule evaluation is retained as a failed attempt.
            // Acquisition/persistence failures roll back; interruption is never converted.
            val calculated =
                recalculatePayrollAmendment(
                    current.summary.firstAssessmentId,
                    year,
                    (corrections as Result.Success).value,
                )
            val now = clock.instant()
            val preview = (calculated as? Result.Success)?.value
            val calculation =
                PayrollAmendmentCalculationSummary(
                    UUID.randomUUID(),
                    id,
                    (previous?.number ?: 0) + 1,
                    expectedVersion,
                    if (preview == null) PayrollAmendmentCalculationStatus.FAILED
                    else PayrollAmendmentCalculationStatus.SUCCEEDED,
                    preview?.calculations?.size ?: 0,
                    preview?.difference,
                    (calculated as? Result.Failed)?.failure,
                    actor.accountId,
                    now,
                    reason,
                )
            val outcome =
                current.copy(
                    summary =
                        current.summary.copy(
                            version = expectedVersion + 1,
                            status =
                                if (preview == null) PayrollAmendmentStatus.DRAFT
                                else PayrollAmendmentStatus.CALCULATED,
                            action =
                                if (preview == null) PayrollAmendmentAction.CALCULATION_FAILED
                                else PayrollAmendmentAction.CALCULATED,
                            latestCalculationId = calculation.id,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = reason,
                        )
                )
            amendments.save(company, outcome, expectedVersion).flatMap { receipt ->
                amendments
                    .recordCalculation(
                        company,
                        PayrollAmendmentCalculationAttempt(calculation, preview),
                    )
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                id,
                                if (preview == null) "payroll.amendment_calculation_failed"
                                else "payroll.amendment_calculated",
                                mapOf(
                                    "calculationId" to calculation.id.toString(),
                                    "version" to receipt.version.toString(),
                                ),
                                reason,
                            ),
                        )
                    }
                    .flatMap { operations.record(actor, key, receipt) }
                    .flatMap {
                        validateCompanySessionActor(actor, access, clock.instant(), security)
                    }
                    .flatMap {
                        requirePayrollMutation(it, "payroll.amend", clock.instant(), security).map {
                            receipt
                        }
                    }
            }
        }
    }
}
