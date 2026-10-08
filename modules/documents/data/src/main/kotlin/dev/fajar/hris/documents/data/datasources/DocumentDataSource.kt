package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.models.DocumentCapacityData
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface DocumentDataSource {
    fun lock(companyId: UUID)

    fun capacity(companyId: UUID, actorId: UUID): DocumentCapacityData

    fun find(companyId: UUID, id: UUID): DocumentsRecord?

    fun revision(companyId: UUID, id: UUID): DocumentRevisionsRecord?

    fun activeRevision(companyId: UUID, documentId: UUID): DocumentRevisionsRecord?

    fun list(
        companyId: UUID,
        employmentId: UUID,
        classifications: Set<String>,
        after: UUID?,
        limit: Int,
    ): List<DocumentsRecord>

    fun revisions(
        companyId: UUID,
        documentId: UUID,
        after: Int?,
        limit: Int,
    ): List<DocumentRevisionsRecord>

    fun insertDocument(row: DocumentsRecord)

    fun incrementRevision(companyId: UUID, id: UUID, version: Long): DocumentsRecord?

    fun insertRevision(row: DocumentRevisionsRecord)

    fun transition(
        companyId: UUID,
        id: UUID,
        version: Long,
        status: String,
    ): DocumentRevisionsRecord?

    fun chunk(companyId: UUID, revisionId: UUID, id: UUID): DocumentUploadChunksRecord?

    fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int): DocumentUploadChunksRecord?

    fun insertChunk(row: DocumentUploadChunksRecord)

    fun insertAttempt(row: DocumentUploadAttemptsRecord)

    fun reserveAttempt(
        companyId: UUID,
        revisionId: UUID,
        id: UUID,
        attempts: Int,
        attemptId: UUID,
        key: String,
        seconds: Int,
    ): DocumentUploadChunksRecord?

    fun progress(
        companyId: UUID,
        revisionId: UUID,
        version: Long,
        offset: Long,
        size: Int,
    ): DocumentRevisionsRecord?

    fun commitChunk(
        companyId: UUID,
        revisionId: UUID,
        id: UUID,
        attemptId: UUID,
        etag: String,
        version: Long,
    ): DocumentUploadChunksRecord?

    fun validationRevision(companyId: UUID, jobId: UUID): DocumentRevisionsRecord?

    fun chunks(companyId: UUID, revisionId: UUID): List<DocumentUploadChunksRecord>

    fun validationAttempts(
        companyId: UUID,
        revisionId: UUID,
    ): List<DocumentValidationAttemptsRecord>

    fun insertValidationAttempt(row: DocumentValidationAttemptsRecord)

    fun scheduleValidation(
        companyId: UUID,
        id: UUID,
        version: Long,
        jobId: UUID,
        attempt: Int,
    ): DocumentRevisionsRecord?

    fun finishValidation(
        companyId: UUID,
        id: UUID,
        version: Long,
        jobId: UUID,
        status: String,
        size: Long,
        sha256: String,
        mediaType: String,
        clean: Boolean,
        engineVersion: String,
        code: String?,
        at: java.time.OffsetDateTime,
    ): DocumentRevisionsRecord?

    fun failValidation(
        companyId: UUID,
        id: UUID,
        version: Long,
        jobId: UUID,
        code: String,
    ): DocumentRevisionsRecord?

    fun publish(companyId: UUID, id: UUID, version: Long, revisionId: UUID): DocumentsRecord?
}
