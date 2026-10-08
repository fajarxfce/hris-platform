package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.util.UUID

class ListDocumentInventories(
    private val inventory: DocumentInventoryRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<DocumentInventoryRun>> {

        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanyCommandActor(actor, it) }
                .flatMap { live -> live.requirePermission("documents.inventory") }
                .flatMap {
                    if (limit !in 1..100)
                        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page_size"))
                    else inventory.list(company, after, limit)
                }
        }
    }
}
