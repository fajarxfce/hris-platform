package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class ListAccountNativeSessions(
    private val sessions: NativeSessionRepository,
    private val accounts: AccountAdministrationRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: NativeSessionPolicy,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, accountId: UUID): Result<List<NativeSession>> {
        if (actor.companyId != null || "identity.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_administrator_required"))
        return transactions.run(actor) {
            for (id in setOf(actor.accountId, accountId).sorted()) {
                val guard = identities.lockAccount(id, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val checked = validatePlatformSessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            if ("identity.manage" !in (checked as Result.Success).value.permissions)
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "platform_administrator_required")
                )
            accounts.get(accountId).flatMap { target ->
                val listed =
                    if (target == null)
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "account_not_found"))
                    else sessions.list(accountId, clock.instant(), policy.maximumSessions)
                validatePlatformSessionActor(actor, access, clock.instant(), security).flatMap {
                    listed
                }
            }
        }
    }
}
