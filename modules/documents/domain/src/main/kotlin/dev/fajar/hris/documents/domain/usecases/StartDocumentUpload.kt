package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import java.util.UUID

class StartDocumentUpload(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val cleanup: ObjectCleanupRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        input: StartDocumentUploadCommand,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val valid = validateDocumentInput(input)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "documents.upload_start",
                operationId,
                listOf(
                    input.documentId.toString(),
                    input.revisionId.toString(),
                    input.employmentId.toString(),
                    input.title,
                    input.classification.name,
                    input.expectedDocumentVersion.toString(),
                    input.fileName,
                    input.mediaType,
                    input.size.toString(),
                    input.sha256,
                    input.reason,
                ),
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

            val foundProfile = profiles.findForEmployee(company, input.employmentId)
            if (foundProfile is Result.Failed) return@run foundProfile
            val profile =
                (foundProfile as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!canManageDocument(live, input.classification, profile.profile.accountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = documents.find(company, input.documentId)
            if (found is Result.Failed) return@run found
            val existing = (found as Result.Success).value
            if (
                existing != null &&
                    (existing.employmentId != input.employmentId ||
                        existing.title != input.title ||
                        existing.classification != input.classification)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_identity_mismatch")
                )
            if ((existing?.version ?: 0L) != input.expectedDocumentVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if ((existing?.revisionCount ?: 0) >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_revision_limit"))
            val active = documents.activeRevision(company, input.documentId)
            if (active is Result.Failed) return@run active
            val previous = (active as Result.Success).value
            val now = clock.instant()
            if (previous != null) {
                if (previous.expiresAt.isAfter(now))
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_upload_active")
                    )
                val expired =
                    documents.transition(
                        company,
                        previous.id,
                        previous.version,
                        DocumentRevisionStatus.EXPIRED,
                    )
                if (expired is Result.Failed) return@run expired
                if ((expired as Result.Success).value == null)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }
            if (previous != null) {
                val recorded =
                    journal.record(
                        actor,
                        ChangeRecord(
                            "document_revision",
                            previous.id,
                            "documents.upload_expired",
                            mapOf("documentId" to input.documentId.toString()),
                        ),
                    )
                if (recorded is Result.Failed) return@run recorded
            }
            val capacity = documents.capacity(company, actor.accountId)
            if (capacity is Result.Failed) return@run capacity
            val usage = (capacity as Result.Success).value
            if (
                (existing == null && usage.documentCount >= 10000) ||
                    usage.activeUploads >= 100 ||
                    usage.activeActorUploads >= 10
            )
                return@run Result.Failed(
                    Failure(FailureKind.RATE_LIMITED, "document_upload_capacity")
                )
            val allocated = cleanup.allocatedBytes(company)
            if (allocated is Result.Failed) return@run allocated
            if (
                (allocated as Result.Success).value + usage.unfilledBytes + input.size >
                    DOCUMENT_COMPANY_BYTES
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_storage_quota"))
            if (existing == null) {
                val created =
                    documents.create(
                        actor,
                        Document(
                            input.documentId,
                            input.employmentId,
                            input.title,
                            input.classification,
                            0,
                            0,
                            actor.accountId,
                            now,
                        ),
                    )
                if (created is Result.Failed) return@run created
            }
            val revision =
                DocumentRevision(
                    input.revisionId,
                    input.documentId,
                    (existing?.revisionCount ?: 0) + 1,
                    input.fileName,
                    input.mediaType,
                    input.size,
                    input.sha256,
                    DocumentRevisionStatus.UPLOADING,
                    0,
                    0,
                    actor.accountId,
                    now,
                    now.plusSeconds(86400),
                    input.reason,
                )
            documents.addRevision(actor, revision, input.expectedDocumentVersion).flatMap { receipt
                ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "document_revision",
                                revision.id,
                                "documents.upload_started",
                                mapOf(
                                    "documentId" to input.documentId.toString(),
                                    "employeeId" to input.employmentId.toString(),
                                ),
                                input.reason,
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
