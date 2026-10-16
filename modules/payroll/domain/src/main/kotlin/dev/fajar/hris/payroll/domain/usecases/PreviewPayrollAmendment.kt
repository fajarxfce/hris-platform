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

class PreviewPayrollAmendment(
    private val policies: PayrollPolicyRepository,
    private val sources: PayrollAmendmentSourceRepository,
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
        assessmentId: UUID,
        observed: List<PayrollAmendmentSourceReference>,
        remuneration: List<PayrollAmendmentRemuneration>,
    ): Result<PayrollAmendmentPreview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollMutation(actor, "payroll.amend", clock.instant(), security)
        if (permission is Result.Failed) return permission
        val submitted = PayrollAmendmentTerms(observed, remuneration)
        val valid = validatePayrollAmendmentProposal(assessmentId, submitted)
        if (valid is Result.Failed) return valid
        val proposal = snapshotPayrollAmendmentTerms(submitted)
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            if (proposal.corrections.any { it.workAdjustmentId != null }) {
                val workGuard = sources.lockWorkPeriods(company, assessmentId)
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
            val found = identities.access(actor.accountId, company)
            if (found is Result.Failed) return@run found
            val access = (found as Result.Success).value
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
            val acquired = sources.sources(company, assessmentId)
            if (acquired is Result.Failed) return@run acquired
            val year = (acquired as Result.Success).value
            val first =
                year.firstOrNull { it.assessmentId == assessmentId }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_assessment_not_found")
                    )
            val beneficiary = people.accountForEmployee(company, first.target.employeeId)
            if (beneficiary is Result.Failed) return@run beneficiary
            if ((beneficiary as Result.Success).value == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            val acquiredAttendance =
                sources.attendance(
                    company,
                    payrollAmendmentAttendanceReferences(proposal.corrections),
                )
            if (acquiredAttendance is Result.Failed) return@run acquiredAttendance
            val attendance = (acquiredAttendance as Result.Success).value
            val corrections =
                preparePayrollAmendmentCorrections(
                    assessmentId,
                    year,
                    proposal.sourceVersions,
                    proposal.corrections,
                    attendance,
                )
            if (corrections is Result.Failed) return@run corrections
            val currentAttendance = requireCurrentPayrollAmendmentAttendance(attendance)
            if (currentAttendance is Result.Failed) return@run currentAttendance
            val preview =
                recalculatePayrollAmendment(
                    assessmentId,
                    year,
                    (corrections as Result.Success).value,
                )
            if (preview is Result.Failed) return@run preview
            val finalAccess = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (finalAccess is Result.Failed) return@run finalAccess
            val finalAssurance =
                requirePayrollMutation(
                    (finalAccess as Result.Success).value,
                    "payroll.amend",
                    clock.instant(),
                    security,
                )
            if (finalAssurance is Result.Failed) return@run finalAssurance
            preview
        }
    }
}
