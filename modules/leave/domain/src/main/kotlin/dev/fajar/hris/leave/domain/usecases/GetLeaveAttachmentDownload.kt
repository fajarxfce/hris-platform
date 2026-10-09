package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.canReadLeaveRequest
import dev.fajar.hris.leave.domain.repositories.LeaveRequestRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Clock
import java.util.UUID

class GetLeaveAttachmentDownload(
    private val requests: LeaveRequestRepository,
    private val documents: DocumentRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, requestId: UUID, revisionId: UUID): Result<DocumentDownload> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
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
            val found = requests.find(company, requestId)
            if (found is Result.Failed) return@run found
            val request =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_attachment_not_found")
                    )
            val attachment =
                request.attachments.singleOrNull { it.revisionId == revisionId }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "leave_attachment_not_found")
                    )
            val now = clock.instant()
            val employeeResult = people.findAtInstant(company, request.employeeId, now)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee = (employeeResult as Result.Success).value
            if (!canReadLeaveRequest(live, request, employee)) {
                val approvalId =
                    if (request.status == LeaveStatus.CANCELLATION_PENDING)
                        requireNotNull(request.cancellationApprovalId)
                    else request.approvalId
                val approvalResult = approvals.find(company, approvalId)
                if (approvalResult is Result.Failed) return@run approvalResult
                val approval =
                    (approvalResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "approval_unavailable")
                        )
                val delegationResult = approvals.delegations(company, actor.accountId, now)
                if (delegationResult is Result.Failed) return@run delegationResult
                val assigned = approval.stages.flatMap { it.assignees }.toSet()
                val grants = members.candidates(company, assigned, emptySet(), assigned.size)
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
                        Failure(FailureKind.NOT_FOUND, "leave_attachment_not_found")
                    )
            }
            val currentRevision = documents.revision(company, revisionId)
            if (currentRevision is Result.Failed) return@run currentRevision
            val revision = (currentRevision as Result.Success).value
            if (
                revision == null ||
                    revision.status != DocumentRevisionStatus.READY ||
                    revision.documentId != attachment.documentId ||
                    revision.size != attachment.size ||
                    revision.sha256 != attachment.sha256
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "leave_attachment_unavailable")
                )

            Result.Success(
                DocumentDownload(
                    attachment.revisionId,
                    attachment.fileName,
                    attachment.mediaType,
                    attachment.size,
                    attachment.sha256,
                )
            )
        }
    }
}
