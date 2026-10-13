package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.approvals.domain.entities.Delegation
import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetExpenseSubmission(
    private val claims: ExpenseClaimRepository,
    private val approvals: ApprovalRepository,
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
        id: UUID,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.AUTHORIZED,
    ): Result<ExpenseSubmissionDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val resource = claims.lock(company, shared = true)
            if (resource is Result.Failed) return@run resource
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val initialAccess =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (initialAccess is Result.Failed) return@run initialAccess
            val found = claims.submission(company, id)
            if (found is Result.Failed) return@run found
            val submission =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                    )
            val foundClaim = claims.find(company, submission.claimId)
            if (foundClaim is Result.Failed) return@run foundClaim
            val claim =
                (foundClaim as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_claim_not_found")
                    )
            val foundApproval = approvals.find(company, submission.approvalId)
            if (foundApproval is Result.Failed) return@run foundApproval
            val approval =
                (foundApproval as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "approval_unavailable")
                    )
            val foundDelegations =
                if (scope == ExpenseClaimReadScope.AUTHORIZED)
                    approvals.delegations(company, actor.accountId, clock.instant())
                else Result.Success(emptyList<Delegation>())
            if (foundDelegations is Result.Failed) return@run foundDelegations
            val delegations = (foundDelegations as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assigned = approval.stages.flatMap { it.assignees }.toSet()
            val parties =
                delegations
                    .filter {
                        it.kind == ApprovalKind.EXPENSE &&
                            it.toAccount == actor.accountId &&
                            it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val accessed = identities.access(actor.accountId, company)
            if (accessed is Result.Failed) return@run accessed
            val access = (accessed as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val foundEmployee = people.findAtInstant(company, claim.employmentId, now)
            if (foundEmployee is Result.Failed) return@run foundEmployee
            val employee = (foundEmployee as Result.Success).value
            val foundGrants =
                if (scope == ExpenseClaimReadScope.AUTHORIZED)
                    members.candidates(company, assigned, emptySet(), assigned.size)
                else Result.Success(emptyList<MemberAccount>())
            if (foundGrants is Result.Failed) return@run foundGrants
            val grants = (foundGrants as Result.Success).value
            if (employee == null || !canReadExpenseClaim(live, employee, today, scope)) {
                if (
                    scope == ExpenseClaimReadScope.SELF ||
                        !isAssignedApprover(live, approval, delegations, grants, now)
                )
                    return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_submission_not_found")
                    )
            }
            val foundBeneficiary = people.accountForEmployee(company, claim.employmentId)
            if (foundBeneficiary is Result.Failed) return@run foundBeneficiary
            val beneficiary = (foundBeneficiary as Result.Success).value
            val foundReviews = claims.reviews(company, id)
            if (foundReviews is Result.Failed) return@run foundReviews
            val hashes = submission.lines.flatMap { it.receipts }.map { it.sha256 }.toSet()
            val foundDuplicates = claims.duplicateReceiptDigests(company, claim.id, hashes)
            if (foundDuplicates is Result.Failed) return@run foundDuplicates
            val duplicates =
                duplicateExpenseReceiptDigests(
                    submission.lines,
                    (foundDuplicates as Result.Success).value,
                )
            val deliveredAt = clock.instant()
            validateCompanySessionActor(actor, access, deliveredAt, security).flatMap { current ->
                val canReadClaim =
                    employee != null &&
                        canReadExpenseClaim(
                            current,
                            employee,
                            deliveredAt.atZone(ZoneId.of(settings.timezone)).toLocalDate(),
                            scope,
                        )
                if (
                    !canReadClaim &&
                        (scope == ExpenseClaimReadScope.SELF ||
                            !isAssignedApprover(
                                current,
                                approval,
                                delegations,
                                grants,
                                deliveredAt,
                            ))
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "expense_submission_not_found"))
                else
                    Result.Success(
                        ExpenseSubmissionDetails(
                            submission,
                            claim,
                            approval,
                            (foundReviews as Result.Success).value,
                            duplicates,
                            canReadClaim,
                            if (scope == ExpenseClaimReadScope.AUTHORIZED)
                                expenseSubmissionAvailableActions(
                                    claim,
                                    submission,
                                    approval,
                                    current,
                                    beneficiary,
                                    delegations,
                                    grants,
                                    deliveredAt,
                                )
                            else emptySet(),
                        )
                    )
            }
        }
    }
}
