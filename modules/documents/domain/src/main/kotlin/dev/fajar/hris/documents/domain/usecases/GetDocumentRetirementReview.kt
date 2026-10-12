package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.documentStatus
import dev.fajar.hris.documents.domain.policies.retireDocumentState
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetDocumentRetirementReview(
    private val retention: DocumentRetentionRepository,
    private val documents: DocumentRepository,
    private val references: DocumentReferenceRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentRetirementReview> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val documentGuard = documents.lock(company, shared = true)
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
            val current =
                validateCompanySessionActor(actor, identity, clock.instant(), security).flatMap {
                    it.requirePermission("documents.retention")
                }
            if (current is Result.Failed) return@run current

            val foundRevision = documents.revision(company, id)
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
            val referenced = references.referenced(company, setOf(id))
            if (referenced is Result.Failed) return@run referenced

            val now = clock.instant()
            val assurance = validateCompanySessionActor(actor, identity, now, security)
            if (assurance is Result.Failed) return@run assurance
            val eligibility =
                retireDocumentState(
                    state,
                    revision,
                    id in (referenced as Result.Success).value,
                    now,
                )
            Result.Success(
                DocumentRetirementReview(
                    id,
                    revision.number,
                    revision.version,
                    documentStatus(revision, now),
                    document.currentRevisionId == id,
                    state,
                    now,
                    (eligibility as? Result.Failed)?.failure?.code,
                )
            )
        }
    }
}
