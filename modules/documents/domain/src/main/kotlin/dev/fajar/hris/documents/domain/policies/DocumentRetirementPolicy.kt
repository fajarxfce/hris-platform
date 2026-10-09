package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import java.time.Instant

fun retireDocumentState(
    state: DocumentRetentionState,
    revision: DocumentRevision,
    referenced: Boolean,
    at: Instant,
): Result<DocumentRetentionState> {
    if (revision.documentId != state.documentId || revision.status != DocumentRevisionStatus.READY)
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_revision_not_ready"))
    val archive =
        state.archive
            ?: return Result.Failed(Failure(FailureKind.CONFLICT, "document_not_archived"))
    if (state.legalHold) return Result.Failed(Failure(FailureKind.CONFLICT, "document_legal_hold"))
    val eligible =
        archive.eligibleAt
            ?: return Result.Failed(Failure(FailureKind.CONFLICT, "document_retention_indefinite"))
    if (at.isBefore(eligible))
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_retention_pending"))
    if (referenced)
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_evidence_referenced"))
    val version =
        state.version
            ?: return Result.Failed(Failure(FailureKind.CONFLICT, "document_not_archived"))
    if (version >= 9999)
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_retention_history_limit"))
    return Result.Success(state.copy(version = version + 1))
}
