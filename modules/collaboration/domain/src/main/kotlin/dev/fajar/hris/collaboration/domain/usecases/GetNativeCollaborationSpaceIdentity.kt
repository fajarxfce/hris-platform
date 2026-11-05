package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceIdentityRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** Reads an existing provider-user intent. A missing selection stays absent; no device grant. */
class GetNativeCollaborationSpaceIdentity(
    private val selections: CollaborationSpaceIdentityRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
    ): Result<CollaborationSpaceIdentity?> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        val started = clock.instant()
        return transactions.run(actor) {
            // Serializes against selection and account/device security changes, without writes.
            val guarded = identities.lockAccount(actor.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed")
                    )
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
                        at,
                    )
                    .flatMap { if (it.spaceId == spaceId) Result.Success(it) else denied() }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val found = selections.selection(spaceId, actor.accountId, provider.serverName)
            // Recheck the original attempt lifetime and current admission for either outcome.
            val afterRead = admission()
            if (afterRead is Result.Failed) return@run afterRead
            if (found is Result.Failed) return@run found
            val input = (found as Result.Success).value
            if (input == null || input.spaceId != spaceId || input.accountId != actor.accountId)
                return@run denied()
            if (input.selected == null) return@run Result.Success(null)
            // The existing pure policy validates only the retained branch here. Never publish
            // the preferred candidate that an explicit selection command would otherwise choose.
            selectCollaborationSpaceIdentity(
                spaceId,
                actor.accountId,
                provider.serverName,
                input,
                clock.instant(),
            )
        }
    }

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
