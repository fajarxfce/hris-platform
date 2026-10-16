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

class SavePayrollAmendment(
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
        firstAssessmentId: UUID,
        expectedVersion: Long?,
        terms: PayrollAmendmentTerms,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val valid = validatePayrollAmendmentDraft(firstAssessmentId, terms, expectedVersion, reason)
        if (valid is Result.Failed) return valid
        val proposal = snapshotPayrollAmendmentTerms(terms)
        val key =
            OperationKey(
                "payroll.amendment_save",
                operationId,
                listOf(
                    id.toString(),
                    firstAssessmentId.toString(),
                    expectedVersion?.toString(),
                    reason,
                ) + payrollAmendmentParts(proposal),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val payrollGuard = policies.lock(company)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            if (proposal.corrections.any { it.workAdjustmentId != null }) {
                val workGuard = sources.lockWorkPeriods(company, firstAssessmentId)
                if (workGuard is Result.Failed) return@run workGuard
            }
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
            // Creation acquires the beneficiary from the immutable assessment. An existing
            // draft carries that identity, so replay does not reacquire or adopt new sources.
            val initialSources =
                if (current == null) sources.sources(company, firstAssessmentId)
                else Result.Success(emptyList())
            if (initialSources is Result.Failed) return@run initialSources
            val initialYear = (initialSources as Result.Success).value
            val employeeId =
                current?.summary?.employee?.id
                    ?: initialYear
                        .firstOrNull { it.assessmentId == firstAssessmentId }
                        ?.target
                        ?.employeeId
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, employeeId)
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
            if (current?.summary?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                current != null &&
                    current.summary.status !in
                        setOf(
                            PayrollAmendmentStatus.DRAFT,
                            PayrollAmendmentStatus.CALCULATED,
                            PayrollAmendmentStatus.REJECTED,
                        )
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_not_editable")
                )
            if ((current?.summary?.version ?: -1) >= PAYROLL_AMENDMENT_MAXIMUM_VERSION - 1)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_revision_limit")
                )
            if (current != null && current.summary.firstAssessmentId != firstAssessmentId)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_basis_changed")
                )
            val acquired =
                if (current == null) initialSources else sources.sources(company, firstAssessmentId)
            if (acquired is Result.Failed) return@run acquired
            val year = (acquired as Result.Success).value
            val acquiredAttendance =
                sources.attendance(
                    company,
                    payrollAmendmentAttendanceReferences(proposal.corrections),
                )
            if (acquiredAttendance is Result.Failed) return@run acquiredAttendance
            val attendance = (acquiredAttendance as Result.Success).value
            val corrections =
                preparePayrollAmendmentCorrections(
                    firstAssessmentId,
                    year,
                    proposal.sourceVersions,
                    proposal.corrections,
                    attendance,
                )
            if (corrections is Result.Failed) return@run corrections
            val currentAttendance = requireCurrentPayrollAmendmentAttendance(attendance)
            if (currentAttendance is Result.Failed) return@run currentAttendance
            val remuneration =
                validatePayrollAmendmentRemuneration((corrections as Result.Success).value, reason)
            if (remuneration is Result.Failed) return@run remuneration
            val first = year.first { it.assessmentId == firstAssessmentId }
            val now = clock.instant()
            val summary =
                PayrollAmendmentSummary(
                    id,
                    firstAssessmentId,
                    first.facts.month,
                    first.personId,
                    current?.summary?.employee
                        ?: PayrollEmployee(
                            first.target.employeeId,
                            first.target.employeeNumber,
                            first.target.employeeName,
                            first.target.employmentVersion,
                        ),
                    (expectedVersion ?: -1) + 1,
                    PayrollAmendmentStatus.DRAFT,
                    PayrollAmendmentAction.SAVED,
                    current?.summary?.latestCalculationId,
                    current?.summary?.createdAt ?: now,
                    actor.accountId,
                    now,
                    reason,
                    current?.summary?.latestReviewId,
                )
            amendments
                .save(company, PayrollAmendment(summary, proposal), expectedVersion)
                .flatMap { receipt ->
                    journal
                        .record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                id,
                                "payroll.amendment_saved",
                                mapOf(
                                    "assessmentId" to firstAssessmentId.toString(),
                                    "version" to receipt.version.toString(),
                                ),
                                reason,
                            ),
                        )
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            validateCompanySessionActor(actor, access, clock.instant(), security)
                        }
                        .flatMap {
                            requirePayrollMutation(it, "payroll.amend", clock.instant(), security)
                                .map { receipt }
                        }
                }
        }
    }
}
