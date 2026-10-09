package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Clock
import java.util.UUID

class RetireDocumentRevision(
    private val retention: DocumentRetentionRepository,
    private val documents: DocumentRepository,
    private val references: DocumentReferenceRepository,
    private val cleanup: ObjectCleanupRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        revisionId: UUID,
        expectedRevisionVersion: Long,
        expectedRetentionVersion: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateDocumentRetentionAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        if (
            expectedRevisionVersion < 0 ||
                expectedRetentionVersion !in 0L..9999L ||
                reason.isBlank() ||
                reason.length > 1000 ||
                reason.any { it.isISOControl() }
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_retirement"))
        val key =
            OperationKey(
                "documents.revision_retire",
                operationId,
                listOf(
                    revisionId.toString(),
                    expectedRevisionVersion.toString(),
                    expectedRetentionVersion.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = documents.lock(company)
            if (guard is Result.Failed) return@run guard
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val permission =
                validateDocumentRetentionAccess(
                    (checked as Result.Success).value,
                    clock.instant(),
                    security,
                )
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val foundRevision = documents.revision(company, revisionId)
            if (foundRevision is Result.Failed) return@run foundRevision
            val revision =
                (foundRevision as Result.Success).value
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
            val stored = retention.state(company, document.id)
            if (stored is Result.Failed) return@run stored
            val state = (stored as Result.Success).value ?: DocumentRetentionState(document.id)
            if (
                revision.version != expectedRevisionVersion ||
                    state.version != expectedRetentionVersion
            )
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val referenced = references.referenced(company, setOf(revision.id))
            if (referenced is Result.Failed) return@run referenced
            val now = clock.instant()
            val planned =
                retireDocumentState(
                    state,
                    revision,
                    revision.id in (referenced as Result.Success).value,
                    now,
                )
            if (planned is Result.Failed) return@run planned
            val next = (planned as Result.Success).value
            val loaded = documents.chunks(company, revision.id)
            if (loaded is Result.Failed) return@run loaded
            val chunks = (loaded as Result.Success).value
            val manifest = documentContentParts(revision, chunks)
            if (manifest is Result.Failed) return@run manifest
            val evidence =
                DocumentRetirement(
                    document.id,
                    revision.id,
                    revision.version + 1,
                    requireNotNull(next.version),
                    actor.accountId,
                    now,
                    now.plusSeconds(300),
                    reason,
                )
            val changed =
                retention.saveState(
                    company,
                    DocumentRetentionChange(
                        next,
                        DocumentRetentionAction.RETIRE,
                        actor.accountId,
                        now,
                        reason,
                        revision.id,
                    ),
                    state.version,
                )
            if (changed is Result.Failed) return@run changed
            val recorded = retention.recordRetirement(company, evidence)
            if (recorded is Result.Failed) return@run recorded
            val retired = documents.retire(company, revision.id, revision.version)
            if (retired is Result.Failed) return@run retired
            if (document.currentRevisionId == revision.id) {
                val cleared = documents.unpublish(company, document, revision.id)
                if (cleared is Result.Failed) return@run cleared
            }
            // One accepted manifest is at most 100 one-MiB parts. No object I/O occurs here.
            for (chunk in chunks) {
                val scheduled =
                    cleanup.schedule(
                        ObjectCleanupRequest(
                            requireNotNull(chunk.attemptId),
                            company,
                            revision.id,
                            requireNotNull(chunk.key),
                            chunk.size.toLong(),
                            actor.accountId,
                            evidence.deleteAfter,
                        )
                    )
                if (scheduled is Result.Failed) return@run scheduled
            }
            val receipt = (retired as Result.Success).value
            operations
                .record(actor, key, receipt)
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "document_revision",
                            revision.id,
                            "documents.revision_retired",
                            mapOf(
                                "documentId" to document.id.toString(),
                                "retentionVersion" to evidence.retentionVersion.toString(),
                                "revisionVersion" to evidence.revisionVersion.toString(),
                            ),
                            reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}
