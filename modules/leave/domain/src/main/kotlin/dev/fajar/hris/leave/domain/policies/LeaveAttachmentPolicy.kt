package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.leave.domain.entities.LeaveAttachment
import java.util.UUID

const val LEAVE_ATTACHMENT_LIMIT = 3

fun validateLeaveAttachmentIds(ids: List<UUID>): Result<Unit> =
    when {
        ids.size > LEAVE_ATTACHMENT_LIMIT ->
            Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_leave_attachments",
                    fields = mapOf("attachmentRevisionIds" to "too_many"),
                    parameters = mapOf("maximum" to LEAVE_ATTACHMENT_LIMIT.toString()),
                )
            )
        ids.toSet().size != ids.size ->
            Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_leave_attachments",
                    fields = mapOf("attachmentRevisionIds" to "duplicate"),
                )
            )
        else -> Result.Success(Unit)
    }

fun snapshotLeaveAttachment(
    document: Document?,
    revision: DocumentRevision?,
    employeeId: UUID,
): Result<LeaveAttachment> {
    if (
        document == null ||
            revision == null ||
            document.id != revision.documentId ||
            document.employmentId != employeeId ||
            document.classification != DocumentClassification.PERSONAL ||
            revision.status != DocumentRevisionStatus.READY
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_attachment_unavailable"))
    return Result.Success(
        LeaveAttachment(
            document.id,
            revision.id,
            revision.fileName,
            revision.mediaType,
            revision.size,
            revision.sha256,
        )
    )
}
