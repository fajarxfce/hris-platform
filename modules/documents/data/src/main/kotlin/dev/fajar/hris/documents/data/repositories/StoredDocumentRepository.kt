package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.DocumentDataSource
import dev.fajar.hris.documents.data.mappers.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.schema.tables.records.DocumentUploadAttemptsRecord
import dev.fajar.hris.schema.tables.records.DocumentValidationAttemptsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredDocumentRepository(private val source: DocumentDataSource) : DocumentRepository {
    override fun lock(companyId: UUID) = safeDatabaseCall { source.lock(companyId) }

    override fun capacity(companyId: UUID, actorId: UUID) = safeDatabaseCall {
        val r = source.capacity(companyId, actorId)
        DocumentCapacity(
            Math.toIntExact(r.documents),
            Math.toIntExact(r.active),
            Math.toIntExact(r.owned),
            r.unfilledBytes,
            r.readyBytes,
        )
    }

    override fun find(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.find(companyId, id)?.toDocument()
    }

    override fun revision(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.revision(companyId, id)?.toDocumentRevision()
    }

    override fun activeRevision(companyId: UUID, documentId: UUID) = safeDatabaseCall {
        source.activeRevision(companyId, documentId)?.toDocumentRevision()
    }

    override fun list(
        companyId: UUID,
        employmentId: UUID,
        classifications: Set<DocumentClassification>,
        after: UUID?,
        limit: Int,
    ) = safeDatabaseCall {
        val rows =
            source.list(
                companyId,
                employmentId,
                classifications.map { it.name }.toSet(),
                after,
                limit + 1,
            )
        Page(
            rows.take(limit).map { it.toDocument() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun revisions(companyId: UUID, documentId: UUID, after: Int?, limit: Int) =
        safeDatabaseCall {
            val rows = source.revisions(companyId, documentId, after, limit + 1)
            Page(
                rows.take(limit).map { it.toDocumentRevision() },
                if (rows.size > limit) rows[limit - 1].revisionNo.toString() else null,
            )
        }

    override fun create(actor: Actor, document: Document) = safeDatabaseCall {
        source.insertDocument(document.toDocumentRow(requireNotNull(actor.companyId)))
    }

    override fun addRevision(
        actor: Actor,
        revision: DocumentRevision,
        expectedDocumentVersion: Long,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.incrementRevision(
                    requireNotNull(actor.companyId),
                    revision.documentId,
                    expectedDocumentVersion,
                )
            }
            .flatMap { updated ->
                if (updated == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else
                    safeDatabaseCall {
                        source.insertRevision(
                            revision.toRevisionRow(requireNotNull(actor.companyId))
                        )
                        MutationReceipt(revision.id, revision.version)
                    }
            }

    override fun transition(
        companyId: UUID,
        id: UUID,
        version: Long,
        status: DocumentRevisionStatus,
    ) = safeDatabaseCall {
        source.transition(companyId, id, version, status.name)?.let {
            MutationReceipt(it.id, it.version)
        }
    }

    override fun chunk(companyId: UUID, revisionId: UUID, id: UUID) = safeDatabaseCall {
        source.chunk(companyId, revisionId, id)?.toDocumentChunk()
    }

    override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) = safeDatabaseCall {
        source.chunkAt(companyId, revisionId, ordinal)?.toDocumentChunk()
    }

    override fun insertChunk(companyId: UUID, chunk: DocumentUploadChunk) = safeDatabaseCall {
        source.insertChunk(chunk.toChunkRow(companyId))
    }

    override fun reserveAttempt(
        companyId: UUID,
        chunk: DocumentUploadChunk,
        attemptId: UUID,
        key: String,
        seconds: Int,
    ): Result<DocumentUploadChunk> =
        safeDatabaseCall {
                source.insertAttempt(
                    DocumentUploadAttemptsRecord().also {
                        it.companyId = companyId
                        it.id = attemptId
                        it.revisionId = chunk.revisionId
                        it.chunkId = chunk.id
                        it.objectKey = key
                        it.byteCount = chunk.size
                    }
                )
                source
                    .reserveAttempt(
                        companyId,
                        chunk.revisionId,
                        chunk.id,
                        chunk.attempts,
                        attemptId,
                        key,
                        seconds,
                    )
                    ?.toDocumentChunk()
            }
            .flatMap { reserved ->
                reserved?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "document_chunk_in_progress"))
            }

    override fun commitChunk(
        companyId: UUID,
        lease: DocumentUploadLease,
        etag: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val updated =
                    source.progress(
                        companyId,
                        lease.revision.id,
                        lease.revision.version,
                        lease.chunk.offset,
                        lease.chunk.size,
                    ) ?: return@safeDatabaseCall null
                source.commitChunk(
                    companyId,
                    lease.revision.id,
                    lease.chunk.id,
                    lease.attemptId,
                    etag,
                    updated.version,
                ) ?: return@safeDatabaseCall null
                MutationReceipt(updated.id, updated.version)
            }
            .flatMap { receipt ->
                receipt?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "document_chunk_lease_lost"))
            }

    override fun validationRevision(companyId: UUID, jobId: UUID) = safeDatabaseCall {
        source.validationRevision(companyId, jobId)?.toDocumentRevision()
    }

    override fun chunks(companyId: UUID, revisionId: UUID) = safeDatabaseCall {
        source.chunks(companyId, revisionId).map { it.toDocumentChunk() }
    }

    override fun validationAttempts(companyId: UUID, revisionId: UUID) = safeDatabaseCall {
        source.validationAttempts(companyId, revisionId).map { it.toValidationAttempt() }
    }

    override fun scheduleValidation(
        actor: Actor,
        revision: DocumentRevision,
        attempt: DocumentValidationAttempt,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.insertValidationAttempt(
                    DocumentValidationAttemptsRecord().also {
                        it.companyId = requireNotNull(actor.companyId)
                        it.revisionId = revision.id
                        it.jobId = attempt.jobId
                        it.attempt = attempt.number
                        it.actorId = actor.accountId
                        it.createdAt = attempt.createdAt.atOffset(ZoneOffset.UTC)
                        it.reason = attempt.reason
                    }
                )
                source
                    .scheduleValidation(
                        requireNotNull(actor.companyId),
                        revision.id,
                        revision.version,
                        attempt.jobId,
                        attempt.number,
                    )
                    ?.let { MutationReceipt(it.id, it.version) }
            }
            .flatMap {
                it?.let { value -> Result.Success(value) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun finishValidation(
        companyId: UUID,
        revision: DocumentRevision,
        status: DocumentRevisionStatus,
        inspection: DocumentInspection,
        code: String?,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source
                    .finishValidation(
                        companyId,
                        revision.id,
                        revision.version,
                        requireNotNull(revision.validationJobId),
                        status.name,
                        inspection.size,
                        inspection.sha256,
                        inspection.mediaType,
                        inspection.clean,
                        inspection.engineVersion,
                        code,
                        at.atOffset(ZoneOffset.UTC),
                    )
                    ?.let { MutationReceipt(it.id, it.version) }
            }
            .flatMap {
                it?.let { value -> Result.Success(value) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "document_validation_obsolete"))
            }

    override fun failValidation(
        companyId: UUID,
        revision: DocumentRevision,
        code: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source
                    .failValidation(
                        companyId,
                        revision.id,
                        revision.version,
                        requireNotNull(revision.validationJobId),
                        code,
                    )
                    ?.let { MutationReceipt(it.id, it.version) }
            }
            .flatMap {
                it?.let { value -> Result.Success(value) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "document_validation_obsolete"))
            }

    override fun publish(companyId: UUID, document: Document, revisionId: UUID): Result<Unit> =
        safeDatabaseCall {
                source.publish(companyId, document.id, document.version, revisionId) != null
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun retire(
        companyId: UUID,
        revisionId: UUID,
        expectedVersion: Long,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.retire(companyId, revisionId, expectedVersion)?.version }
            .requireCurrentVersion()
            .map { MutationReceipt(revisionId, it) }

    override fun unpublish(companyId: UUID, document: Document, revisionId: UUID): Result<Unit> =
        safeDatabaseCall {
                source.unpublish(companyId, document.id, document.version, revisionId)?.version
            }
            .requireCurrentVersion()
            .map { Unit }
}
