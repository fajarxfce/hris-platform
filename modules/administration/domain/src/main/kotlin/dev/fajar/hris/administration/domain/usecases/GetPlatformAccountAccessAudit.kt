package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.PlatformAccountAccessAudit
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class GetPlatformAccountAccessAudit(
    private val audits: PlatformAuditRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<PlatformAccountAccessAudit> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val original =
            actor.requirePermission("audit.read").flatMap {
                actor.requirePermission("identity.manage")
            }
        if (original is Result.Failed) return original
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val found = identities.access(actor.accountId, null)
            if (found is Result.Failed) return@run found
            val access = (found as Result.Success).value
            fun authorized(): Result<Unit> =
                validatePlatformSessionActor(actor, access, clock.instant(), security).flatMap {
                    current ->
                    current.requirePermission("audit.read").flatMap {
                        current.requirePermission("identity.manage")
                    }
                }
            val checked = authorized()
            if (checked is Result.Failed) return@run checked
            audits.accountAccess(id).flatMap { detail ->
                authorized().flatMap {
                    if (detail == null)
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "audit_detail_not_found"))
                    else Result.Success(detail)
                }
            }
        }
    }
}
