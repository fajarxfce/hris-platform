package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
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

class GetPayrollAmendmentReview(
    private val amendments: PayrollAmendmentRepository,
    private val reviews: PayrollAmendmentReviewRepository,
    private val policies: PayrollPolicyRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<PayrollAmendmentReviewDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = requirePayrollAmendmentRead(actor)
        if (permission is Result.Failed) return permission
        return transactions.run(actor) {
            val payrollGuard = policies.lock(company, shared = true)
            if (payrollGuard is Result.Failed) return@run payrollGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val found = reviews.find(company, id)
            if (found is Result.Failed) return@run found
            val review = (found as Result.Success).value
            val foundApproval =
                review?.let { approvals.find(company, it.approvalId) } ?: Result.Success(null)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval = (foundApproval as Result.Success).value
            val delegated = approvals.delegations(company, actor.accountId, clock.instant())
            if (delegated is Result.Failed) return@run delegated
            val delegations = (delegated as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assigned = approval?.stages?.flatMap { it.assignees }?.toSet() ?: emptySet()
            val decidingParties =
                delegations
                    .filter {
                        it.kind == ApprovalKind.PAYROLL_AMENDMENT && it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in decidingParties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val foundAccess = identities.access(actor.accountId, company)
            if (foundAccess is Result.Failed) return@run foundAccess
            val access = (foundAccess as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val allowed = requirePayrollAmendmentRead(live)
            if (allowed is Result.Failed) return@run allowed
            if (review == null)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "payroll_amendment_review_not_found")
                )
            if (approval == null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_unavailable"))
            val amendmentId = review.amendmentId
            val foundAmendment = amendments.summary(company, amendmentId)
            if (foundAmendment is Result.Failed) return@run foundAmendment
            val amendment =
                (foundAmendment as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_amendment_not_found")
                    )
            val owner = people.accountForEmployee(company, amendment.employee.id)
            if (owner is Result.Failed) return@run owner
            val beneficiary = (owner as Result.Success).value
            if (beneficiary == actor.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "payroll_amendment_self_preparation_forbidden")
                )
            val foundCalculation =
                amendments.calculationSummary(company, amendmentId, review.calculationId)
            if (foundCalculation is Result.Failed) return@run foundCalculation
            val calculation =
                (foundCalculation as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "payroll_amendment_calculation_missing")
                    )
            val foundHistory = reviews.changes(company, id)
            if (foundHistory is Result.Failed) return@run foundHistory
            val history = (foundHistory as Result.Success).value
            val displayParties =
                assigned +
                    history.flatMap { listOfNotNull(it.actorId, it.decidingFor) } +
                    review.submittedBy
            val parties = displayParties + decidingParties
            if (parties.size > 2000)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "approval_group_too_large"))
            val candidates = members.candidates(company, parties, emptySet(), parties.size)
            if (candidates is Result.Failed) return@run candidates
            val accounts = (candidates as Result.Success).value
            val now = clock.instant()
            validateCompanySessionActor(actor, access, now, security).map {
                PayrollAmendmentReviewDetails(
                    review,
                    amendment,
                    calculation,
                    approval,
                    history,
                    accounts
                        .filter { it.id in displayParties }
                        .map { PayrollReviewAccount(it.id, it.displayName) },
                    availablePayrollAmendmentReviewActions(
                        live,
                        amendment,
                        review,
                        payrollAmendmentDecisionScope(approval, beneficiary),
                        delegations,
                        accounts,
                        now,
                    ),
                )
            }
        }
    }
}
