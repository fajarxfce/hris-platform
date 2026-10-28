package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.IntegrationConfigurationReview
import dev.fajar.hris.administration.domain.repositories.IntegrationConfigurationRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock

class GetIntegrationConfiguration(
    private val configuration: IntegrationConfigurationRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<IntegrationConfigurationReview> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validatePlatformSessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if ("identity.manage" !in (checked as Result.Success).value.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            configuration.read().flatMap { items ->
                val at = clock.instant()
                validatePlatformSessionActor(actor, access, at, security).map {
                    IntegrationConfigurationReview(at, items)
                }
            }
        }
    }
}
