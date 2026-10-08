package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID

class ListDocuments(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        employmentId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<Document>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            if (limit !in 1..200)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_page"))
            val found = profiles.findForEmployee(company, employmentId)
            if (found is Result.Failed) return@run found
            val profile =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val classifications =
                DocumentClassification.entries
                    .filter { canReadDocument(live, it, profile.profile.accountId) }
                    .toSet()
            if (classifications.isEmpty())
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            documents.list(company, employmentId, classifications, after, limit)
        }
    }
}
