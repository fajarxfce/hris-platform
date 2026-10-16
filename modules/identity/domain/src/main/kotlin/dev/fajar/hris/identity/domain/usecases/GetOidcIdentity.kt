package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.entities.OidcIdentity
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.time.Clock
import java.util.UUID

class GetOidcIdentity(
    private val links: OidcIdentityRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, accountId: UUID, id: UUID): Result<OidcIdentity> {
        if (
            actor.companyId != null ||
                (actor.accountId != accountId && "identity.manage" !in actor.permissions)
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validatePlatformSessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            if (actor.accountId != accountId && "identity.manage" !in live.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            links.find(id).flatMap { identity ->
                validatePlatformSessionActor(actor, access, clock.instant(), security).flatMap {
                    if (identity == null || identity.accountId != accountId)
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "oidc_identity_not_found"))
                    else Result.Success(identity)
                }
            }
        }
    }
}
