package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionState
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class GetDocumentRetention(
    private val retention: DocumentRetentionRepository,
    private val documents: DocumentRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentRetentionState> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { it.requirePermission("documents.retention") }
                .flatMap {
                    documents.find(company, id).flatMap { document ->
                        if (document == null)
                            Result.Failed(Failure(FailureKind.NOT_FOUND, "document_not_found"))
                        else retention.state(company, id).map { it ?: DocumentRetentionState(id) }
                    }
                }
        }
    }
}
