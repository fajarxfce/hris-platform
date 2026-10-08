package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.util.UUID

class ReadDocumentContent(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val identities: IdentityRepository,
    private val storage: ObjectStorageRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID, offset: Long, length: Int): Result<ByteArray> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (offset !in 0 until DOCUMENT_MAXIMUM_BYTES || length !in 1..DOCUMENT_CHUNK_BYTES)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_range"))
        val prepared =
            transactions.run(actor) {
                val current =
                    identities.access(actor.accountId, company).flatMap {
                        validateDocumentActor(actor, it)
                    }
                if (current is Result.Failed) return@run current
                val live = (current as Result.Success).value

                val found = documents.revision(company, id)
                if (found is Result.Failed) return@run found
                val revision =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "document_revision_not_found")
                        )
                val foundDocument = documents.find(company, revision.documentId)
                if (foundDocument is Result.Failed) return@run foundDocument
                val document =
                    (foundDocument as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "document_not_found")
                        )
                val profile = profiles.findForEmployee(company, document.employmentId)
                if (profile is Result.Failed) return@run profile
                if (
                    !canReadDocument(
                        live,
                        document.classification,
                        (profile as Result.Success).value?.profile?.accountId,
                    )
                )
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "document_access_denied")
                    )
                if (revision.status != DocumentRevisionStatus.READY)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_not_ready"))
                if (
                    offset + length > revision.size ||
                        offset / DOCUMENT_CHUNK_BYTES !=
                            (offset + length - 1) / DOCUMENT_CHUNK_BYTES
                )
                    return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_document_range")
                    )
                val ordinal = (offset / DOCUMENT_CHUNK_BYTES + 1).toInt()
                val foundPart = documents.chunkAt(company, id, ordinal)
                if (foundPart is Result.Failed) return@run foundPart
                val part = (foundPart as Result.Success).value
                if (part == null || !part.committed || part.key == null || part.etag == null)
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
        return transactions.run(actor) {
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            val found = documents.revision(company, id)
            if (found is Result.Failed) return@run found
            val revision =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_revision_not_found")
                    )
            val foundDocument = documents.find(company, revision.documentId)
            if (foundDocument is Result.Failed) return@run foundDocument
            val document =
                (foundDocument as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val profile = profiles.findForEmployee(company, document.employmentId)
            if (profile is Result.Failed) return@run profile
            if (
                !canReadDocument(
                    live,
                    document.classification,
                    (profile as Result.Success).value?.profile?.accountId,
                )
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            if (revision.status != DocumentRevisionStatus.READY)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_not_ready"))
            val from = (offset - part.offset).toInt()
            Result.Success(
                if (from == 0 && length == bytes.size) bytes
                else bytes.copyOfRange(from, from + length)
            )
        }
    }
}
