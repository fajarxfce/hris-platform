package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateSessionAssurance
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

class ResolveActor(
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(
        accountId: UUID,
        companyId: UUID?,
        authenticatedAt: Instant,
        correlationId: UUID,
        mfaVerifiedAt: Instant? = null,
        credentialVersion: Long? = null,
        requireAssurance: Boolean = true,
    ): Result<Actor> {
        val scope =
            Actor(
                accountId,
                companyId,
                emptySet(),
                authenticatedAt,
                correlationId,
                mfaVerifiedAt,
                credentialVersion,
            )
        return transactions.run(scope) {
            identities.access(accountId, companyId).flatMap { access ->
                when {
                    access == null ||
                        !access.account.active ||
                        (credentialVersion != null &&
                            credentialVersion != access.account.securityVersion) ->
                        Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked"))
                    companyId != null && (!access.membershipActive || !access.companyActive) ->
                        Result.Failed(Failure(FailureKind.FORBIDDEN, "company_access_denied"))
                    requireAssurance ->
                        validateSessionAssurance(
                                access.account,
                                access.securityPermissions,
                                mfaVerifiedAt,
                                clock.instant(),
                                policy,
                            )
                            .map {
                                scope.copy(
                                    permissions = access.permissions,
                                    platformPermissions = access.platformPermissions,
                                )
                            }
                    else ->
                        Result.Success(
                            scope.copy(
                                permissions = access.permissions,
                                platformPermissions = access.platformPermissions,
                            )
                        )
                }
            }
        }
    }
}
