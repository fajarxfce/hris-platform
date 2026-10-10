package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.policies.isAssignedApprover
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.canReadLeaveRequest
import dev.fajar.hris.leave.domain.repositories.LeaveRequestRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.time.Clock
import java.util.UUID

class ReadLeaveAttachmentContent(
    private val requests: LeaveRequestRepository,
    private val documents: DocumentRepository,
    private val people: PeopleRepository,
    private val approvals: ApprovalRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val storage: ObjectStorageRepository,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        requestId: UUID,
        revisionId: UUID,
        offset: Long,
        length: Int,
    ): Result<ByteArray> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (offset !in 0 until DOCUMENT_MAXIMUM_BYTES || length !in 1..DOCUMENT_CHUNK_BYTES)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_range"))
        val prepared =
            transactions.run(actor) {
                val peopleLock = people.lockReportingLines(company, shared = true)
                if (peopleLock is Result.Failed) return@run peopleLock
                val approvalLock = approvals.lock(company, shared = true)
                if (approvalLock is Result.Failed) return@run approvalLock
                val companyLock = companies.lock(company, shared = true)
                if (companyLock is Result.Failed) return@run companyLock
                val memberLock = members.lock(company, shared = true)
                if (memberLock is Result.Failed) return@run memberLock
                val accountLock = identities.lockAccount(actor.accountId, shared = true)
                if (accountLock is Result.Failed) return@run accountLock
                val checked =
                    identities.access(actor.accountId, company).flatMap {
                        validateCompanySessionActor(actor, it, clock.instant(), security)
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

                if (
                    offset + length > attachment.size ||
                        offset / DOCUMENT_CHUNK_BYTES !=
                            (offset + length - 1) / DOCUMENT_CHUNK_BYTES
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_document_range")
                    )
                val foundPart =
                    documents.chunkAt(
                        company,
                        revisionId,
                        (offset / DOCUMENT_CHUNK_BYTES + 1).toInt(),
                    )
                if (foundPart is Result.Failed) return@run foundPart
                val part = (foundPart as Result.Success).value
                if (
                    part == null ||
                        !part.committed ||
                        part.key == null ||
                        part.etag == null ||
                        part.offset > offset ||
                        part.offset + part.size < offset + length
                )
                    return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "document_manifest_invalid")
                    )
                Result.Success(
                    DocumentContentPart(
                        requireNotNull(part.key),
                        part.offset,
                        part.size,
                        part.sha256,
                        requireNotNull(part.etag),
                    )
                )
            }
        if (prepared is Result.Failed) return prepared
        val part = (prepared as Result.Success).value
        val content = storage.read(part.key, 0, part.size, part.etag)
        if (content is Result.Failed) return content
        val bytes = (content as Result.Success).value
        if (
            bytes.size != part.size ||
                java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) !=
                    part.sha256
        )
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "document_content_integrity_failure")
            )
        // Storage reads happen outside a database transaction; access is checked again afterward.
        return transactions.run(actor) {
            val peopleLock = people.lockReportingLines(company, shared = true)
            if (peopleLock is Result.Failed) return@run peopleLock
            val approvalLock = approvals.lock(company, shared = true)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company, shared = true)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company, shared = true)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId, shared = true)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
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

            val from = (offset - part.offset).toInt()
            Result.Success(
                if (from == 0 && length == bytes.size) bytes
                else bytes.copyOfRange(from, from + length)
            )
        }
    }
}
