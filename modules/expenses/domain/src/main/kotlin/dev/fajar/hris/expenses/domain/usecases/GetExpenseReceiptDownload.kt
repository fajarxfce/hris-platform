package dev.fajar.hris.expenses.domain.usecases

import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.policies.canReadExpenseClaim
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
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
) {
    fun execute(actor: Actor, submissionId: UUID, revisionId: UUID): Result<DocumentDownload> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
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
            if (employee == null || !canReadExpenseClaim(live, employee, today)) {
                val approvalResult = approvals.find(company, receipt.approvalId)
                if (approvalResult is Result.Failed) return@run approvalResult
                val approval =
                    (approvalResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "expense_receipt_not_found")
                        )
                val delegationResult = approvals.delegations(company, actor.accountId, now)
                if (delegationResult is Result.Failed) return@run delegationResult
                val assignees = approval.stages.flatMap { it.assignees }.toSet()
                val grants = members.candidates(company, assignees, emptySet(), assignees.size)
                if (grants is Result.Failed) return@run grants
                if (
                    !isAssignedApprover(
                        live,
                        approval,
                        (delegationResult as Result.Success).value,
                        (grants as Result.Success).value,
                        now,
                    )
                )
                    return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "expense_receipt_not_found")
                    )
            }
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
            Result.Success(
                DocumentDownload(
                    receipt.revisionId,
                    receipt.fileName,
                    receipt.mediaType,
                    receipt.size,
                    receipt.sha256,
                )
            )
        }
    }
}
