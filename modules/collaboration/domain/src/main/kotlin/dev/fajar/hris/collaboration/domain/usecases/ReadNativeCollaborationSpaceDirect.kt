package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** Current native reads of owned conversation intent and original-room metadata, never content. */
class ReadNativeCollaborationSpaceDirect(
    private val conversations: CollaborationSpaceConversationRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationSpaceDirectConversation>> {
        if (limit !in 1..50 || after == UUID(0, 0)) return invalidQuery()
        return read(actor, sessionId, sessionVersion, spaceId, client) {
            conversations.list(spaceId, actor.accountId, after, limit)
        }
    }

    fun get(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        conversationId: UUID,
    ): Result<CollaborationSpaceDirectConversation> {
        if (conversationId == UUID(0, 0)) return invalidQuery()
        return read(actor, sessionId, sessionVersion, spaceId, client) {
            conversations.find(spaceId, actor.accountId, conversationId).flatMap {
                if (it != null) Result.Success(it) else missing()
            }
        }
    }

    fun legacy(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        after: CollaborationLegacyConversationPosition?,
        limit: Int,
    ): Result<CollaborationSpaceLegacyConversationPage> {
        if (
            limit !in 1..50 ||
                after?.let { it.companyId == UUID(0, 0) || it.conversationId == UUID(0, 0) } == true
        )
            return invalidQuery()
        return read(actor, sessionId, sessionVersion, spaceId, client) {
            conversations.legacy(spaceId, actor.accountId, after, limit)
        }
    }

    fun legacyOne(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        position: CollaborationLegacyConversationPosition,
    ): Result<CollaborationSpaceLegacyConversation> {
        if (position.companyId == UUID(0, 0) || position.conversationId == UUID(0, 0))
            return invalidQuery()
        return read(actor, sessionId, sessionVersion, spaceId, client) {
            conversations.legacyOne(spaceId, actor.accountId, position).flatMap {
                if (it != null) Result.Success(it) else missing()
            }
        }
    }

    private fun invalidQuery() =
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query"))

    private fun missing() =
        Result.Failed(Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found"))

    private fun <T> read(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        acquire: () -> Result<T>,
    ): Result<T> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (spaceId == UUID(0, 0)) return invalidQuery()
        return transactions.run(actor) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<Unit> {
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
                        if (it.spaceId == spaceId) Result.Success(Unit)
                        else
                            Result.Failed(
                                Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                            )
                    }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val result = acquire()
            // Current denial takes precedence on either acquisition outcome.
            admission().flatMap { result }
        }
    }
}
