package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class ListDocuments(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
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
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val identity = identities.access(actor.accountId, company)
            if (identity is Result.Failed) return@run identity
            val resolved = (identity as Result.Success).value
            val current = validateCompanySessionActor(actor, resolved, clock.instant(), security)
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
            documents.list(company, employmentId, classifications, after, limit).flatMap { result ->
                validateCompanySessionActor(actor, resolved, clock.instant(), security).map {
                    result
                }
            }
        }
    }
}
