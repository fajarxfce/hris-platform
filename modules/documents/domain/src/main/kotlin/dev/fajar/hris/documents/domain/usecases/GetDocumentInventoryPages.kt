package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class GetDocumentInventoryPages(
    private val inventory: DocumentInventoryRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        id: UUID,
        after: Int,
        limit: Int,
    ): Result<List<DocumentInventoryPage>> {

        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { live -> live.requirePermission("documents.inventory") }
                .flatMap {
                    if (after !in 0..10000 || limit !in 1..100)
                        return@flatMap Result.Failed(
                            Failure(FailureKind.VALIDATION, "invalid_page_size")
                        )
                    inventory.find(company, id).flatMap { value ->
                        if (value == null)
                            Result.Failed(
                                Failure(FailureKind.NOT_FOUND, "document_inventory_not_found")
                            )
                        else inventory.pages(company, id, after, limit)
                    }
                }
        }
    }
}
