package dev.fajar.hris.documents.data.mappers

import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun DocumentsRecord.toDocument() =
    Document(
        id,
        employmentId,
        title,
        DocumentClassification.valueOf(classification),
        revisionCount,
        version,
        createdBy,
        createdAt.toInstant(),
        currentRevisionId,
    )

fun DocumentRevisionsRecord.toDocumentRevision() =
    DocumentRevision(
        id,
        documentId,
        revisionNo,
        fileName,
        mediaType,
        expectedBytes,
        expectedSha256,
        DocumentRevisionStatus.valueOf(status),
        uploadedBytes,
        version,
        createdBy,
        createdAt.toInstant(),
        expiresAt.toInstant(),
        reason,
        validationJobId,
        validationAttempts,
        failureCode,
        if (validatedAt == null) null
        else
            DocumentInspection(
                requireNotNull(contentBytes),
                requireNotNull(contentSha256),
                requireNotNull(detectedMediaType),
                requireNotNull(scanClean),
                requireNotNull(scannerVersion),
            ),
        validatedAt?.toInstant(),
    )

fun DocumentUploadChunksRecord.toDocumentChunk() =
    DocumentUploadChunk(
        id,
        revisionId,
        ordinal,
        byteOffset,
        byteCount,
        sha256,
        createdBy,
        status == "COMMITTED",
        attempts,
        currentAttemptId,
        objectKey,
        leaseUntil?.toInstant(),
        etag,
        committedVersion,
    )

fun Document.toDocumentRow(company: UUID) =
    DocumentsRecord().also { r ->
        r.companyId = company
        r.id = id
        r.employmentId = employmentId
        r.title = title
        r.classification = classification.name
        r.revisionCount = revisionCount
        r.version = version
        r.createdBy = createdBy
        r.createdAt = createdAt.atOffset(ZoneOffset.UTC)
    }

fun DocumentRevision.toRevisionRow(company: UUID) =
    DocumentRevisionsRecord().also { r ->
        r.companyId = company
        r.id = id
        r.documentId = documentId
        r.revisionNo = number
        r.fileName = fileName
        r.mediaType = mediaType
        r.expectedBytes = size
        r.expectedSha256 = sha256
        r.status = status.name
        r.uploadedBytes = uploadedBytes
        r.version = version
        r.createdBy = createdBy
        r.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        r.expiresAt = expiresAt.atOffset(ZoneOffset.UTC)
        r.reason = reason
    }

fun DocumentUploadChunk.toChunkRow(company: UUID) =
    DocumentUploadChunksRecord().also { r ->
        r.companyId = company
        r.revisionId = revisionId
        r.id = id
        r.ordinal = number
        r.byteOffset = offset
        r.byteCount = size
        r.sha256 = sha256
        r.createdBy = createdBy
        r.status = if (committed) "COMMITTED" else "PENDING"
        r.attempts = attempts
        r.currentAttemptId = attemptId
        r.objectKey = key
        r.leaseUntil = leaseUntil?.atOffset(ZoneOffset.UTC)
        r.etag = etag
        r.committedVersion = committedVersion
    }

fun DocumentValidationAttemptsRecord.toValidationAttempt() =
    DocumentValidationAttempt(jobId, attempt, actorId, createdAt.toInstant(), reason)
