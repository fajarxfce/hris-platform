package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** Public directory only. The contact query is one live snapshot, not a reserved room grant. */
class ListNativeCollaborationSpaceContacts(
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceContact>> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (query.length > 80 || query.any(Char::isISOControl) || limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_contact_query")
            )
        return transactions.run(actor) {
            // Never acquire company/member guards after account/session guards. Directory
            // eligibility and each returned page use fresh restricted statement snapshots.
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                return admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        sessionId,
                        sessionVersion,
                        (current as Result.Success).value,
                        client,
                        policy,
                        security,
                        clock.instant(),
                    )
                    .flatMap {
                        if (it.spaceId == spaceId) Result.Success(it)
                        else
                            Result.Failed(
                                Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                            )
                    }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            spaces.contacts(spaceId, actor.accountId, query.trim(), after, limit).flatMap { page ->
                admission().map { page }
            }
        }
    }
}
