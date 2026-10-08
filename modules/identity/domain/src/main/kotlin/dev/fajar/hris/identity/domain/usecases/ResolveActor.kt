package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Instant
import java.util.UUID

class ResolveActor(
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        accountId: UUID,
        companyId: UUID?,
        authenticatedAt: Instant,
        correlationId: UUID,
    ): Result<Actor> {
        val scope = Actor(accountId, companyId, emptySet(), authenticatedAt, correlationId)
        return transactions.run(scope) {
            identities.access(accountId, companyId).flatMap { access ->
                when {
                    access == null || !access.account.active ->
                        Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
                    companyId != null && (!access.membershipActive || !access.companyActive) ->
                        Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
                    else -> Result.Success(scope.copy(permissions = access.permissions))
                }
            }
        }
    }
}
