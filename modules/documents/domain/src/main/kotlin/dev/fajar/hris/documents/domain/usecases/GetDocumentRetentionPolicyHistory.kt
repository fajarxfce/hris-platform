package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionPolicy
import dev.fajar.hris.documents.domain.repositories.DocumentRetentionRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class GetDocumentRetentionPolicyHistory(
    private val retention: DocumentRetentionRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
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
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            identities
                .access(actor.accountId, company)
                .flatMap { validateCompanySessionActor(actor, it, clock.instant(), security) }
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
