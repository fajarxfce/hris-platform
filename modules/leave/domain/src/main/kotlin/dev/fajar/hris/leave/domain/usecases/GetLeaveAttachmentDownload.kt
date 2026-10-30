package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.canReadLeaveRequest
import dev.fajar.hris.leave.domain.policies.hasLeaveRequestReadScope
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
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, requestId: UUID, revisionId: UUID): Result<DocumentDownload> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company, shared = true)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val admitted =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (admitted is Result.Failed) return@run admitted
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
            val acquiredAt = clock.instant()
            val employeeResult = people.findAtInstant(company, request.employeeId, acquiredAt)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee = (employeeResult as Result.Success).value
            var approval: ApprovalRequest? = null
            var delegations = emptyList<Delegation>()
            var assigned = emptySet<UUID>()
            if (!canReadLeaveRequest((admitted as Result.Success).value, request, employee)) {
                val approvalId =
                    if (request.status == LeaveStatus.CANCELLATION_PENDING)
                        requireNotNull(request.cancellationApprovalId)
                    else request.approvalId
                val approvalResult = approvals.find(company, approvalId)
                if (approvalResult is Result.Failed) return@run approvalResult
                approval =
                    (approvalResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "approval_unavailable")
                        )
                val delegationResult = approvals.delegations(company, actor.accountId, acquiredAt)
                if (delegationResult is Result.Failed) return@run delegationResult
                delegations = (delegationResult as Result.Success).value
                assigned = approval.stages.flatMap { it.assignees }.toSet()
            }
            val parties =
                (if (delegations.size <= 200) delegations else emptyList())
                    .filter {
                        it.kind == approval?.kind &&
                            it.toAccount == actor.accountId &&
                            it.fromAccount in assigned
                    }
                    .map { it.fromAccount }
                    .toSet() + actor.accountId
            for (party in parties.sorted()) {
                val accountGuard = identities.lockAccount(party, shared = true)
                if (accountGuard is Result.Failed) return@run accountGuard
            }
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val grantsResult =
                if (approval == null) Result.Success(emptyList<MemberAccount>())
                else members.candidates(company, parties, emptySet(), parties.size)
            if (grantsResult is Result.Failed) return@run grantsResult
            val grants = (grantsResult as Result.Success).value
            if (
                !hasLeaveRequestReadScope(
                    live,
                    request,
                    employee,
                    approval,
                    delegations,
                    grants,
                    clock.instant(),
                )
            )
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "leave_attachment_not_found")
                )
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

            val completedAt = clock.instant()
            val completed = validateCompanySessionActor(actor, access, completedAt, security)
            if (completed is Result.Failed) return@run completed
            if (
                !hasLeaveRequestReadScope(
                    (completed as Result.Success).value,
                    request,
                    employee,
                    approval,
                    delegations,
                    grants,
                    completedAt,
                )
            )
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "leave_attachment_not_found")
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
