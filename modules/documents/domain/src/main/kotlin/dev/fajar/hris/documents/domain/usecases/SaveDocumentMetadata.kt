package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class SaveDocumentMetadata(
    private val metadata: DocumentMetadataRepository,
    private val documents: DocumentRepository,
    private val retention: DocumentRetentionRepository,
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        documentId: UUID,
        expectedVersion: Long,
        input: DocumentBusinessMetadata,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            expectedVersion < 0 ||
                expectedVersion == Long.MAX_VALUE ||
                reason.isBlank() ||
                reason.length > 1000 ||
                reason.any(Char::isISOControl)
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_document_metadata_change")
            )
        val selected = normalizeDocumentMetadata(input)
        val valid = validateDocumentMetadata(selected)
        if (valid is Result.Failed) return valid
        val key =
            OperationKey(
                "documents.metadata_save",
                operationId,
                listOf(documentId.toString(), expectedVersion.toString(), reason) +
                    documentMetadataKey(selected),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val documentGuard = documents.lock(company)
            if (documentGuard is Result.Failed) return@run documentGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, company)
            if (access is Result.Failed) return@run access
            val identity = (access as Result.Success).value
            val current = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value
            val found = documents.find(company, documentId)
            if (found is Result.Failed) return@run found
            val document =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val foundProfile = profiles.findForEmployee(company, document.employmentId)
            if (foundProfile is Result.Failed) return@run foundProfile
            val owner = (foundProfile as Result.Success).value?.profile?.accountId
            if (!canManageDocument(live, document.classification, owner))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            val assured = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (assured is Result.Failed) return@run assured
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (document.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (document.metadataVersion >= 999)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_metadata_limit"))
            val storedRetention = retention.state(company, documentId)
            if (storedRetention is Result.Failed) return@run storedRetention
            if ((storedRetention as Result.Success).value?.archive != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_archived"))
            if (document.metadata == selected)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_metadata_unchanged")
                )
            val revision =
                DocumentMetadataRevision(
                    documentId,
                    document.metadataVersion + 1,
                    document.version + 1,
                    selected,
                    actor.accountId,
                    clock.instant(),
                    reason,
                )
            metadata.save(actor, expectedVersion, revision).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "document",
                                documentId,
                                "documents.metadata_saved",
                                mapOf(
                                    "version" to receipt.version.toString(),
                                    "metadataVersion" to revision.version.toString(),
                                ),
                                reason,
                            ),
                        )
                    }
                    .flatMap {
                        validateCompanySessionActor(actor, identity, clock.instant(), security)
                    }
                    .map { receipt }
            }
        }
    }
}
