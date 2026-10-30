package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.policies.canReadExpenseClaim
import dev.fajar.hris.expenses.domain.policies.validateExpenseReceiptRead
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.*
import java.util.UUID

class GetExpenseReceiptDownload(
    private val claims: ExpenseClaimRepository,
    private val documents: DocumentRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val people: PeopleRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        submissionId: UUID,
        revisionId: UUID,
        scope: ExpenseClaimReadScope = ExpenseClaimReadScope.AUTHORIZED,
    ): Result<DocumentDownload> {
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
            val documentGuard = documents.lock(company, shared = true)
            if (documentGuard is Result.Failed) return@run documentGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val initialAccess =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (initialAccess is Result.Failed) return@run initialAccess
            val found = claims.receipt(company, submissionId, revisionId)
            if (found is Result.Failed) return@run found
            val receipt =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_receipt_not_found")
                    )
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val now = clock.instant()
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val employeeResult = people.findAtInstant(company, receipt.employmentId, now)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee = (employeeResult as Result.Success).value
            val ordinary =
                employee != null &&
                    canReadExpenseClaim(
                        (initialAccess as Result.Success).value,
                        employee,
                        today,
                        scope,
                    )
            val approvalResult: Result<ApprovalRequest?> =
                if (!ordinary && scope == ExpenseClaimReadScope.AUTHORIZED)
                    approvals.find(company, receipt.approvalId)
                else Result.Success(null)
            if (approvalResult is Result.Failed) return@run approvalResult
            val approval = (approvalResult as Result.Success).value
            val delegationResult =
                if (approval != null) approvals.delegations(company, actor.accountId, now)
                else Result.Success(emptyList<Delegation>())
            if (delegationResult is Result.Failed) return@run delegationResult
            val delegations = (delegationResult as Result.Success).value
            if (delegations.size > 200)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "approval_delegation_capacity")
                )
            val assignees = approval?.stages?.flatMap { it.assignees }?.toSet() ?: emptySet()
            val parties =
                delegations
                    .filter {
                        it.kind == ApprovalKind.EXPENSE &&
                            it.toAccount == actor.accountId &&
                            it.fromAccount in assignees
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
            val grantsResult =
                if (approval != null)
                    members.candidates(company, assignees, emptySet(), assignees.size)
                else Result.Success(emptyList<MemberAccount>())
            if (grantsResult is Result.Failed) return@run grantsResult
            val grants = (grantsResult as Result.Success).value
            val admittedAt = clock.instant()
            val admitted =
                validateExpenseReceiptRead(
                    live,
                    employee,
                    admittedAt.atZone(ZoneId.of(settings.timezone)).toLocalDate(),
                    scope,
                    approval,
                    delegations,
                    grants,
                    admittedAt,
                )
            if (admitted is Result.Failed) return@run admitted
            val currentRevision = documents.revision(company, revisionId)
            if (currentRevision is Result.Failed) return@run currentRevision
            val revision = (currentRevision as Result.Success).value
            if (
                revision == null ||
                    revision.status != DocumentRevisionStatus.READY ||
                    revision.size != receipt.size ||
                    revision.sha256 != receipt.sha256
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "expense_receipt_unavailable")
                )
            validateCompanySessionActor(actor, access, clock.instant(), security)
                .flatMap { current ->
                    val deliveredAt = clock.instant()
                    validateExpenseReceiptRead(
                        current,
                        employee,
                        deliveredAt.atZone(ZoneId.of(settings.timezone)).toLocalDate(),
                        scope,
                        approval,
                        delegations,
                        grants,
                        deliveredAt,
                    )
                }
                .map {
                    DocumentDownload(
                        receipt.revisionId,
                        receipt.fileName,
                        receipt.mediaType,
                        receipt.size,
                        receipt.sha256,
                    )
                }
        }
    }
}
