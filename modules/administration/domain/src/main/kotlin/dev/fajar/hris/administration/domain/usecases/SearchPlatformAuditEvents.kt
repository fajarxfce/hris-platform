package dev.fajar.hris.administration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.administration.domain.repositories.PlatformAuditRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock

class SearchPlatformAuditEvents(
    private val audits: PlatformAuditRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, input: AuditSearch): Result<PlatformAuditPage> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val original = actor.requirePermission("audit.read")
        if (original is Result.Failed) return original
        val prepared = prepareAuditQuery(input, clock.instant())
        if (prepared is Result.Failed) return prepared
        val query = (prepared as Result.Success).value
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked =
                validatePlatformSessionActor(actor, access, clock.instant(), security).flatMap {
                    it.requirePermission("audit.read")
                }
            if (checked is Result.Failed) return@run checked
            val scoped =
                if (input.cursor == null) Result.Success(query)
                else audits.find(input.cursor).flatMap { validateAuditCursor(query, it) }
            if (scoped is Result.Failed) return@run scoped
            audits.search((scoped as Result.Success).value).flatMap { page ->
                val at = clock.instant()
                validatePlatformSessionActor(actor, access, at, security)
                    .flatMap { it.requirePermission("audit.read") }
                    .map {
                        PlatformAuditPage(query.from, query.until, at, page.items, page.nextCursor)
                    }
            }
        }
    }
}
