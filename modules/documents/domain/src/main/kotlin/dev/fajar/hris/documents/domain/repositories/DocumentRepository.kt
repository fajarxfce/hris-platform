package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import java.util.UUID

interface DocumentRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun capacity(companyId: UUID, actorId: UUID): Result<DocumentCapacity>

    fun find(companyId: UUID, id: UUID): Result<Document?>

    fun revision(companyId: UUID, id: UUID): Result<DocumentRevision?>

    fun activeRevision(companyId: UUID, documentId: UUID): Result<DocumentRevision?>

    fun list(
        companyId: UUID,
        employmentId: UUID,
        classifications: Set<DocumentClassification>,
        after: UUID?,
        limit: Int,
    ): Result<Page<Document>>

    fun revisions(
        companyId: UUID,
        documentId: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<DocumentRevision>>

    fun create(actor: Actor, document: Document): Result<Unit>

    fun addRevision(
        actor: Actor,
        revision: DocumentRevision,
        expectedDocumentVersion: Long,
    ): Result<MutationReceipt>

    fun transition(
        companyId: UUID,
        id: UUID,
        version: Long,
        status: DocumentRevisionStatus,
    ): Result<MutationReceipt?>

    fun chunk(companyId: UUID, revisionId: UUID, id: UUID): Result<DocumentUploadChunk?>

    fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int): Result<DocumentUploadChunk?>

    fun insertChunk(companyId: UUID, chunk: DocumentUploadChunk): Result<Unit>

    fun reserveAttempt(
        companyId: UUID,
        chunk: DocumentUploadChunk,
        attemptId: UUID,
        key: String,
        seconds: Int,
    ): Result<DocumentUploadChunk>

    fun commitChunk(
        companyId: UUID,
        lease: DocumentUploadLease,
        etag: String,
    ): Result<MutationReceipt>
}
