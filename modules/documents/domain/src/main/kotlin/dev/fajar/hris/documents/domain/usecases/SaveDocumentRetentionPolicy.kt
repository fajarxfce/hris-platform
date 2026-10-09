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

class SaveDocumentRetentionPolicy(
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
        id: UUID,
        classification: DocumentClassification,
        expectedVersion: Long?,
        retentionDays: Int?,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateDocumentRetentionAccess(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        val valid = validateDocumentRetentionPolicy(retentionDays, reason)
        if (valid is Result.Failed) return valid
        if (expectedVersion != null && expectedVersion !in 0L..9999L)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_document_retention_policy")
            )
        val key =
            OperationKey(
                "documents.retention_policy_save",
                operationId,
                listOf(
                    id.toString(),
                    classification.name,
                    expectedVersion?.toString() ?: "new",
                    retentionDays?.toString() ?: "indefinite",
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
            val found = retention.policy(company, id)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (previous?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (previous != null && previous.classification != classification)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_retention_classification_immutable")
                )
            if (previous == null) {
                val configured = retention.policyFor(company, classification)
                if (configured is Result.Failed) return@run configured
                if ((configured as Result.Success).value != null)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_retention_policy_exists")
                    )
            }
            if ((previous?.version ?: -1) >= 9999)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_retention_history_limit")
                )
            val policy =
                DocumentRetentionPolicy(
                    id,
                    classification,
                    (previous?.version ?: -1) + 1,
                    retentionDays,
                    actor.accountId,
                    clock.instant(),
                    reason,
                )
            retention.savePolicy(company, policy, expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "document_retention_policy",
                                id,
                                "documents.retention_policy_saved",
                                mapOf(
                                    "classification" to classification.name,
                                    "version" to policy.version.toString(),
                                    "retentionDays" to (retentionDays?.toString() ?: "indefinite"),
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
