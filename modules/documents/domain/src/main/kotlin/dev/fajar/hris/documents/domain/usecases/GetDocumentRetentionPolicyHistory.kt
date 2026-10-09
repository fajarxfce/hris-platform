package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionPolicy
import dev.fajar.hris.documents.domain.repositories.DocumentRetentionRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class GetDocumentRetentionPolicyHistory(
    private val retention: DocumentRetentionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<DocumentRetentionPolicy>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { it.requirePermission("documents.retention") }
                .flatMap {
                    if ((after != null && after !in 0L..9999L) || limit !in 1..200)
                        return@flatMap Result.Failed(
                            Failure(FailureKind.VALIDATION, "invalid_page_size")
                        )
                    retention.policy(company, id).flatMap { policy ->
                        if (policy == null)
                            Result.Failed(
                                Failure(
                                    FailureKind.NOT_FOUND,
                                    "document_retention_policy_not_found",
                                )
                            )
                        else retention.policyHistory(company, id, after, limit)
                    }
                }
        }
    }
}
