package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.identity.domain.entities.AccountAccess
import java.time.Instant
import java.util.UUID

const val DOCUMENT_CHUNK_BYTES = 1048576
const val DOCUMENT_MAXIMUM_BYTES = 104857600L
const val DOCUMENT_COMPANY_BYTES = 10737418240L

fun canReadDocument(
    actor: Actor,
    classification: DocumentClassification,
    accountId: UUID?,
): Boolean =
    ("documents.read" in actor.permissions && "people.profile.read" in actor.permissions) ||
        ("documents.self.read" in actor.permissions &&
            accountId == actor.accountId &&
            classification != DocumentClassification.HR_ONLY)

fun canManageDocument(
    actor: Actor,
    classification: DocumentClassification,
    accountId: UUID?,
): Boolean =
    ("documents.manage" in actor.permissions && "people.profile.manage" in actor.permissions) ||
        ("documents.self.upload" in actor.permissions &&
            accountId == actor.accountId &&
            classification != DocumentClassification.HR_ONLY)

fun validateDocumentActor(actor: Actor, access: AccountAccess?): Result<Actor> {
    if (
        access == null ||
            !access.account.active ||
            (actor.credentialVersion != null &&
                actor.credentialVersion != access.account.securityVersion)
    )
        return Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
    if (!access.companyActive || !access.membershipActive)
        return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
    return Result.Success(actor.copy(permissions = access.permissions))
}

fun validateDocumentInput(input: StartDocumentUploadCommand): Result<Unit> {
    val extension =
        when (input.mediaType) {
            "application/pdf" -> setOf("pdf")
            "image/jpeg" -> setOf("jpg", "jpeg")
            "image/png" -> setOf("png")
            else -> emptySet()
        }
    if (
        input.expectedDocumentVersion < 0 ||
            input.title.isBlank() ||
            input.title.length > 160 ||
            input.fileName.isBlank() ||
            input.fileName.length > 180 ||
            input.fileName.any { it.isISOControl() || it == '/' || it == '\\' } ||
            input.fileName.substringAfterLast('.', "").lowercase() !in extension ||
            input.size !in 1..DOCUMENT_MAXIMUM_BYTES ||
            !input.sha256.matches(Regex("[0-9a-f]{64}")) ||
            input.reason.isBlank() ||
            input.reason.length > 1000
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_upload"))
    return Result.Success(Unit)
}

fun documentStatus(revision: DocumentRevision, at: Instant): DocumentRevisionStatus =
    if (revision.status in ACTIVE_DOCUMENT_STATUSES && !revision.expiresAt.isAfter(at))
        DocumentRevisionStatus.EXPIRED
    else revision.status

val ACTIVE_DOCUMENT_STATUSES =
    setOf(
        DocumentRevisionStatus.UPLOADING,
        DocumentRevisionStatus.VALIDATING,
        DocumentRevisionStatus.VALIDATION_FAILED,
    )

fun projectDocumentRevision(
    revision: DocumentRevision,
    job: dev.fajar.hris.jobs.domain.entities.BackgroundJob?,
    at: Instant,
): DocumentRevision {
    if (documentStatus(revision, at) == DocumentRevisionStatus.EXPIRED)
        return revision.copy(status = DocumentRevisionStatus.EXPIRED)
    return if (
        revision.status == DocumentRevisionStatus.VALIDATING &&
            job?.status in
                setOf(
                    dev.fajar.hris.jobs.domain.entities.JobStatus.FAILED,
                    dev.fajar.hris.jobs.domain.entities.JobStatus.CANCELLED,
                )
    )
        revision.copy(
            status = DocumentRevisionStatus.VALIDATION_FAILED,
            failureCode = job?.failureCode ?: "document_validation_stopped",
        )
    else revision
}
