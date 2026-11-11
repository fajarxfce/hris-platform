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

class ReadNativeCollaborationSpaceRoom(
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
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
        parentId: UUID,
        parentVersion: Long,
        spaceId: UUID,
        query: CollaborationSpaceRoomQuery,
        client: ClientVersion?,
    ): Result<CollaborationSpaceRoomView> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val validQuery =
            when (query) {
                is CollaborationSpaceRoomQuery.Conversation -> query.id != UUID(0, 0)
                is CollaborationSpaceRoomQuery.ProviderRoom ->
                    validCollaborationSpaceRoomId(query.id)
            }
        if (listOf(parentId, spaceId).any { it == UUID(0, 0) } || parentVersion < 0 || !validQuery)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
        val started = clock.instant()
        return transactions.run(actor) {
            // An immutable lookup chooses the room guard; it grants no authority. No account or
            // native lock may precede that room guard. Recheck the exact binding under the guard.
            val selection: Result<Pair<UUID, CollaborationSpaceBoundRoom?>> =
                when (query) {
                    is CollaborationSpaceRoomQuery.Conversation -> Result.Success(query.id to null)
                    is CollaborationSpaceRoomQuery.ProviderRoom ->
                        rooms
                            .boundRoom(spaceId, actor.accountId, provider.serverName, query.id)
                            .flatMap {
                                snapshotCollaborationSpaceBoundRoom(
                                    spaceId,
                                    actor.accountId,
                                    provider.serverName,
                                    query.id,
                                    it,
                                    clock.instant(),
                                )
                            }
                            .map { it.intent.conversationId to it }
                }
            val found =
                selection.flatMap { (id, _) ->
                    rooms.lock(spaceId, id).flatMap {
                        conversations.find(spaceId, actor.accountId, id)
                    }
                }
            val conversation = (found as? Result.Success)?.value
            val accounts =
                conversation?.let { listOf(it.firstAccountId, it.secondAccountId) }
                    ?: listOf(actor.accountId)
            for (account in accounts.sorted()) {
                val guarded = identities.lockAccount(account, shared = true)
                if (guarded is Result.Failed) return@run guarded
            }
            val locked = sessions.lock(parentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun nativeAdmission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30)) return changed()
                val native =
                    admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        parentId,
                        parentVersion,
                        (current as Result.Success).value,
                        client,
                        policy,
                        security,
                        at,
                    )
                if (native is Result.Failed) return native
                if ((native as Result.Success).value.spaceId != spaceId) return denied()
                return native
            }
            fun admission(): Result<Unit> {
                val before = nativeAdmission()
                if (before is Result.Failed) return before
                if (conversation == null) return Result.Success(Unit)
                fun peers(): Result<Unit> {
                    for (account in
                        listOf(conversation.firstAccountId, conversation.secondAccountId)) {
                        val member = conversations.member(spaceId, account)
                        if (member is Result.Failed) return member
                        val value = (member as Result.Success).value
                        if (value == null || value.spaceId != spaceId || value.accountId != account)
                            return Result.Failed(
                                Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                            )
                    }
                    return Result.Success(Unit)
                }
                val observed = peers()
                val current = nativeAdmission()
                if (current is Result.Failed) return current
                return observed
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            fun acquire(): Result<CollaborationSpaceRoomView> {
                // Failed/absent lookups cannot bypass current native denial.
                if (selection is Result.Failed) return selection
                if (found is Result.Failed) return found
                if (conversation == null)
                    return Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
                val (conversationId, selectedBinding) = (selection as Result.Success).value
                val readIntent = rooms.intent(spaceId, actor.accountId, conversationId)
                if (readIntent is Result.Failed) return readIntent
                val readStatus = rooms.status(spaceId, actor.accountId, conversationId)
                if (readStatus is Result.Failed) return readStatus
                val readBinding = rooms.binding(spaceId, actor.accountId, conversationId)
                if (readBinding is Result.Failed) return readBinding
                if (
                    selectedBinding != null &&
                        ((readIntent as Result.Success).value != selectedBinding.intent ||
                            (readBinding as Result.Success).value != selectedBinding.binding)
                )
                    return changed()
                return collaborationSpaceRoomView(
                    spaceId,
                    conversationId,
                    conversation,
                    (readIntent as Result.Success).value,
                    (readStatus as Result.Success).value,
                    (readBinding as Result.Success).value,
                    provider.serverName,
                    clock.instant(),
                )
            }
            val result = acquire()
            admission().flatMap { result }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
