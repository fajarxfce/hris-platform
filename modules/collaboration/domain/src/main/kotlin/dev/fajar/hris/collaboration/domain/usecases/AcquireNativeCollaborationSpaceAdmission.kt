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

/**
 * Discovers the currently eligible group without selecting an identity or granting device access.
 */
class AcquireNativeCollaborationSpaceAdmission(
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
        client: ClientVersion?,
    ): Result<CollaborationSpaceAdmission> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        val started = clock.instant()
        return transactions.run(actor) {
            val guarded = identities.lockAccount(actor.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val access = identities.access(actor.accountId, null)
            if (access is Result.Failed) return@run access
            val current = spaces.current()
            if (current is Result.Failed) return@run current
            val at = clock.instant()
            if (at < started || at >= started.plusSeconds(30))
                return@run Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed")
                )
            admitCollaborationSpaceSession(
                actor,
                (access as Result.Success).value,
                (locked as Result.Success).value,
                sessionId,
                sessionVersion,
                (current as Result.Success).value,
                client,
                policy,
                security,
                at,
            )
        }
    }
}
