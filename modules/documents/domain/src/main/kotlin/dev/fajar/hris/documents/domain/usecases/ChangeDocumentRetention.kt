package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class ChangeDocumentRetention(
    private val retention: DocumentRetentionRepository,
    private val documents: DocumentRepository,
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
        documentId: UUID,
        action: DocumentRetentionAction,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateDocumentRetentionAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        if (
            (expectedVersion != null && expectedVersion !in 0L..9999L) ||
                reason.isBlank() ||
                reason.length > 1000 ||
                reason.any { it.isISOControl() }
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_document_retention_change")
            )
        val key =
            OperationKey(
                "documents.retention_change",
                operationId,
                listOf(
                    documentId.toString(),
                    action.name,
                    expectedVersion?.toString() ?: "initial",
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
            val found = documents.find(company, documentId)
            if (found is Result.Failed) return@run found
            val document =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val stored = retention.state(company, documentId)
            if (stored is Result.Failed) return@run stored
            val previous = (stored as Result.Success).value ?: DocumentRetentionState(documentId)
            if (previous.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val policy =
                if (action == DocumentRetentionAction.ARCHIVE) {
                    val active = documents.activeRevision(company, documentId)
                    if (active is Result.Failed) return@run active
                    // Explicitly cancel unfinished uploads, including expired ones, before
                    // archival.
                    if ((active as Result.Success).value != null)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "document_upload_active")
                        )
                    val configured = retention.policyFor(company, document.classification)
                    if (configured is Result.Failed) return@run configured
                    (configured as Result.Success).value
                } else null
            val now = clock.instant()
            transitionDocumentRetention(previous, action, policy, now)
                .flatMap { next ->
                    retention.saveState(
                        company,
                        DocumentRetentionChange(next, action, actor.accountId, now, reason),
                        expectedVersion,
                    )
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "document",
                                    documentId,
                                    "documents.retention_changed",
                                    mapOf(
                                        "action" to action.name,
                                        "version" to receipt.version.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
