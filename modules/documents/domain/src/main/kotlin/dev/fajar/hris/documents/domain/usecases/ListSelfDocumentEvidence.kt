package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class ListSelfDocumentEvidence(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        employmentId: UUID,
        classification: DocumentClassification,
        after: UUID?,
        limit: Int,
    ): Result<SelfDocumentEvidencePage> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("documents.self.read")
        if (permission is Result.Failed) return permission
        if (classification == DocumentClassification.HR_ONLY)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_classification"))
        if (limit !in 1..50)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_page"))
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
            val checked =
                validateCompanySessionActor(actor, resolved, clock.instant(), security).flatMap {
                    live ->
                    live.requirePermission("documents.self.read").map { live }
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val found = profiles.findForEmployee(company, employmentId)
            if (found is Result.Failed) return@run found
            val profile = (found as Result.Success).value
            if (profile?.profile?.accountId != live.accountId)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            documents
                .published(company, employmentId, classification, after, limit)
                .map {
                    SelfDocumentEvidencePage(
                        live.accountId,
                        company,
                        employmentId,
                        classification,
                        it,
                    )
                }
                .flatMap { result ->
                    validateCompanySessionActor(actor, resolved, clock.instant(), security).map {
                        result
                    }
                }
        }
    }
}
