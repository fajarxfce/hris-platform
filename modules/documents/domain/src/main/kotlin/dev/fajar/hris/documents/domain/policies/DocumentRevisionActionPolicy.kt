package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

/** Current review guidance; every command still owns authorization, replay and mutation guards. */
fun documentRevisionActions(
    actor: Actor,
    classification: DocumentClassification,
    ownerAccountId: UUID?,
    revision: DocumentRevision,
    validationStatus: JobStatus?,
    at: Instant,
    upload: DocumentUploadChunk? = null,
): List<DocumentRevisionAction> {
    if (
        !canManageDocument(actor, classification, ownerAccountId) ||
            revision.status !in ACTIVE_DOCUMENT_STATUSES
    )
        return emptyList()
    return buildList {
        if (
            documentStatus(revision, at) == DocumentRevisionStatus.UPLOADING &&
                revision.uploadedBytes < revision.size &&
                (upload == null ||
                    (!upload.committed &&
                        upload.createdBy == actor.accountId &&
                        upload.attempts < 8))
        )
            add(DocumentRevisionAction.UPLOAD_CONTENT)
        if (
            documentStatus(revision, at) in ACTIVE_DOCUMENT_STATUSES &&
                revision.uploadedBytes == revision.size &&
                revision.validationAttempts < 8 &&
                (revision.validationJobId == null ||
                    validationStatus in setOf(JobStatus.FAILED, JobStatus.CANCELLED))
        )
            add(DocumentRevisionAction.VALIDATE)
        // An expired but still active upload must be explicitly closed before archival.
        add(DocumentRevisionAction.CANCEL_UPLOAD)
    }
}
