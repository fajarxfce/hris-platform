package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import java.util.UUID

class UploadDocumentChunk(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val storage: ObjectStorageRepository,
    private val cleanup: ObjectCleanupRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        revisionId: UUID,
        offset: Long,
        sha256: String,
        body: ByteArray,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            body.isEmpty() ||
                body.size > DOCUMENT_CHUNK_BYTES ||
                offset < 0 ||
                offset >= DOCUMENT_MAXIMUM_BYTES ||
                offset % DOCUMENT_CHUNK_BYTES != 0L ||
                !sha256.matches(Regex("[0-9a-f]{64}"))
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_chunk"))
        val bytes = body.copyOf()
        val observed =
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        if (observed != sha256)
            return Result.Failed(Failure(FailureKind.VALIDATION, "document_chunk_digest_mismatch"))
        val key =
            OperationKey(
                "documents.chunk",
                operationId,
                listOf(revisionId.toString(), offset.toString(), bytes.size.toString(), sha256),
            )
        val preparation: Result<DocumentChunkPreparation> =
            transactions.run(actor) {
                val replay = operations.lockAndReplay(actor, key)
                if (replay is Result.Failed) return@run replay

                val documentLock = documents.lock(company)
                if (documentLock is Result.Failed) return@run documentLock
                val companyLock = companies.lock(company)
                if (companyLock is Result.Failed) return@run companyLock
                val membershipLock = members.lock(company)
                if (membershipLock is Result.Failed) return@run membershipLock
                val accountLock = identities.lockAccount(actor.accountId)
                if (accountLock is Result.Failed) return@run accountLock
                val current =
                    identities.access(actor.accountId, company).flatMap {
                        validateDocumentActor(actor, it)
                    }
                if (current is Result.Failed) return@run current
                val live = (current as Result.Success).value

                val revisionResult = documents.revision(company, revisionId)
                if (revisionResult is Result.Failed) return@run revisionResult
                val revision =
                    (revisionResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "document_revision_not_found")
                        )
                val documentResult = documents.find(company, revision.documentId)
                if (documentResult is Result.Failed) return@run documentResult
                val document =
                    (documentResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "document_not_found")
                        )
                val profileResult = profiles.findForEmployee(company, document.employmentId)
                if (profileResult is Result.Failed) return@run profileResult
                val profile =
                    (profileResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "employee_not_found")
                        )
                if (!canManageDocument(live, document.classification, profile.profile.accountId))
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "document_access_denied")
                    )
                (replay as Result.Success).value?.let {
                    return@run Result.Success(DocumentChunkPreparation.Replayed(it))
                }
                if (documentStatus(revision, clock.instant()) != DocumentRevisionStatus.UPLOADING)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_upload_closed")
                    )
                if (
                    offset != revision.uploadedBytes ||
                        bytes.size.toLong() !=
                            minOf(DOCUMENT_CHUNK_BYTES.toLong(), revision.size - offset)
                )
                    return@run Result.Failed(
                        Failure(
                            FailureKind.CONFLICT,
                            "document_offset_mismatch",
                            mapOf("offset" to revision.uploadedBytes.toString()),
                        )
                    )
                val prior = documents.chunk(company, revisionId, operationId)
                if (prior is Result.Failed) return@run prior
                var chunk = (prior as Result.Success).value
                if (
                    chunk != null &&
                        (chunk.offset != offset ||
                            chunk.size != bytes.size ||
                            chunk.sha256 != sha256 ||
                            chunk.createdBy != actor.accountId)
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "idempotency_payload_mismatch")
                    )
                if (chunk == null) {
                    val ordinal = (offset / DOCUMENT_CHUNK_BYTES + 1).toInt()
                    val occupied = documents.chunkAt(company, revisionId, ordinal)
                    if (occupied is Result.Failed) return@run occupied
                    if ((occupied as Result.Success).value != null)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "document_chunk_reserved")
                        )
                    chunk =
                        DocumentUploadChunk(
                            operationId,
                            revisionId,
                            ordinal,
                            offset,
                            bytes.size,
                            sha256,
                            actor.accountId,
                            false,
                            0,
                            null,
                            null,
                            null,
                            null,
                            null,
                        )
                    val created = documents.insertChunk(company, chunk)
                    if (created is Result.Failed) return@run created
                }
                if (chunk.attempts >= 8)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_chunk_attempts_exhausted")
                    )
                val nextAttempt = chunk.leaseUntil
                if (nextAttempt != null && nextAttempt.isAfter(clock.instant()))
                    return@run Result.Failed(
                        Failure(
                            FailureKind.CONFLICT,
                            "document_chunk_in_progress",
                            mapOf("retryAt" to nextAttempt.toString()),
                        )
                    )
                val attempt = UUID.randomUUID()
                val objectKey = "$company/$revisionId/$attempt"
                val reserved = documents.reserveAttempt(company, chunk, attempt, objectKey, 120)
                if (reserved is Result.Failed) return@run reserved
                val acquired = (reserved as Result.Success).value
                val registration =
                    cleanup.schedule(
                        ObjectCleanupRequest(
                            attempt,
                            company,
                            revisionId,
                            objectKey,
                            bytes.size.toLong(),
                            actor.accountId,
                            revision.expiresAt.plusSeconds(300),
                        )
                    )
                if (registration is Result.Failed) return@run registration
                val capacity = documents.capacity(company, actor.accountId)
                if (capacity is Result.Failed) return@run capacity
                val allocated = cleanup.allocatedBytes(company)
                if (allocated is Result.Failed) return@run allocated
                if (
                    (allocated as Result.Success).value +
                        (capacity as Result.Success).value.unfilledBytes > DOCUMENT_COMPANY_BYTES
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_storage_quota")
                    )
                Result.Success(
                    DocumentChunkPreparation.Reserved(
                        DocumentUploadLease(revision, acquired, attempt, objectKey)
                    )
                )
            }
        if (preparation is Result.Failed) return preparation
        val outcome = (preparation as Result.Success).value
        if (outcome is DocumentChunkPreparation.Replayed) return Result.Success(outcome.receipt)
        val lease = (outcome as DocumentChunkPreparation.Reserved).lease
        // SQL locks are released before writing. A failed writer leaves a bounded lease and a
        // registered garbage key; a later attempt receives a different key.
        val written = storage.put(lease.key, bytes)
        if (written is Result.Failed) return written
        val stored = (written as Result.Success).value
        if (stored.size != bytes.size.toLong() || stored.sha256 != sha256)
            return Result.Failed(
                Failure(FailureKind.UNEXPECTED, "document_storage_integrity_failure")
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay

            val documentLock = documents.lock(company)
            if (documentLock is Result.Failed) return@run documentLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            val documentResult = documents.find(company, lease.revision.documentId)
            if (documentResult is Result.Failed) return@run documentResult
            val document =
                (documentResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val profileResult = profiles.findForEmployee(company, document.employmentId)
            if (profileResult is Result.Failed) return@run profileResult
            val profile =
                (profileResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!canManageDocument(live, document.classification, profile.profile.accountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val applied = documents.commitChunk(company, lease, stored.etag)
            if (applied is Result.Failed) return@run applied
            val receipt = (applied as Result.Success).value
            operations
                .record(actor, key, receipt)
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "document_revision",
                            revisionId,
                            "documents.chunk_committed",
                            mapOf(
                                "offset" to offset.toString(),
                                "version" to receipt.version.toString(),
                            ),
                        ),
                    )
                }
                .map { receipt }
        }
    }
}
