package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.WorkerIntegrationRegistry
import dev.fajar.hris.administration.domain.repositories.WorkerIntegrationRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock

class GetWorkerIntegrationRegistry(
    private val registry: WorkerIntegrationRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<WorkerIntegrationRegistry> {
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
            registry.read().flatMap { snapshot ->
                validatePlatformSessionActor(actor, access, clock.instant(), security).map {
                    snapshot
                }
            }
        }
    }
}
