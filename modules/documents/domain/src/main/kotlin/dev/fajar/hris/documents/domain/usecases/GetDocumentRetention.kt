package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionState
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class GetDocumentRetention(
    private val retention: DocumentRetentionRepository,
    private val documents: DocumentRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentRetentionState> {
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
            val identity = identities.access(actor.accountId, company)
            if (identity is Result.Failed) return@run identity
            val resolved = (identity as Result.Success).value
            validateCompanySessionActor(actor, resolved, clock.instant(), security)
                .flatMap { it.requirePermission("documents.retention") }
                .flatMap {
                    documents.find(company, id).flatMap { document ->
                        if (document == null)
                            Result.Failed(Failure(FailureKind.NOT_FOUND, "document_not_found"))
                        else retention.state(company, id).map { it ?: DocumentRetentionState(id) }
                    }
                }
                .flatMap { result ->
                    validateCompanySessionActor(actor, resolved, clock.instant(), security).map {
                        result
                    }
                }
        }
    }
}
