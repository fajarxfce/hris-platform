package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionPolicy
import dev.fajar.hris.documents.domain.repositories.DocumentRetentionRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository

class GetDocumentRetentionPolicies(
    private val retention: DocumentRetentionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor): Result<List<DocumentRetentionPolicy>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { it.requirePermission("documents.retention") }
                .flatMap { retention.policies(company) }
        }
    }
}
