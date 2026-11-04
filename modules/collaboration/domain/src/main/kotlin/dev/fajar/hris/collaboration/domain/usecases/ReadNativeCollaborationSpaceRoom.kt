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
        conversationId: UUID,
        client: ClientVersion?,
    ): Result<CollaborationSpaceRoomView> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (listOf(parentId, spaceId, conversationId).any { it == UUID(0, 0) } || parentVersion < 0)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
        val started = clock.instant()
        return transactions.run(actor) {
            val roomGuard = rooms.lock(spaceId, conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            val found = conversations.find(spaceId, actor.accountId, conversationId)
            if (found is Result.Failed) return@run found
            val conversation =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
            for (account in
                listOf(conversation.firstAccountId, conversation.secondAccountId).sorted()) {
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
                val readIntent = rooms.intent(spaceId, actor.accountId, conversationId)
                if (readIntent is Result.Failed) return readIntent
                val readStatus = rooms.status(spaceId, actor.accountId, conversationId)
                if (readStatus is Result.Failed) return readStatus
                val readBinding = rooms.binding(spaceId, actor.accountId, conversationId)
                if (readBinding is Result.Failed) return readBinding
                val intent = (readIntent as Result.Success).value
                val status = (readStatus as Result.Success).value
                val binding = (readBinding as Result.Success).value
                if ((intent == null) != (status == null)) return changed()
                if (
                    intent != null &&
                        (intent.spaceId != spaceId ||
                            intent.conversationId != conversationId ||
                            intent.firstAccountId != conversation.firstAccountId ||
                            intent.secondAccountId != conversation.secondAccountId ||
                            intent.serverName != provider.serverName ||
                            intent.createdAt > clock.instant())
                )
                    return changed()
                if (
                    status != null &&
                        (status.spaceId != spaceId ||
                            status.conversationId != conversationId ||
                            status.requestedAt > clock.instant() ||
                            status.completedAt?.let { it > clock.instant() } == true)
                )
                    return changed()
                if (
                    (binding != null) !=
                        (status?.state == CollaborationSpaceProvisioningState.SUCCEEDED)
                )
                    return changed()
                if (
                    binding != null &&
                        (intent == null ||
                            status == null ||
                            binding.spaceId != spaceId ||
                            binding.conversationId != conversationId ||
                            binding.publishedBy != intent.requestedBy ||
                            binding.requestRevision != status.revision ||
                            binding.createdAt > clock.instant() ||
                            binding.createdAt < intent.createdAt)
                )
                    return changed()
                val room =
                    binding?.let {
                        val owner = requireNotNull(intent)
                        CollaborationSpacePublishedRoom(
                            it.roomId,
                            owner.serverName,
                            owner.origin,
                            it.createdAt,
                        )
                    }
                return Result.Success(
                    CollaborationSpaceRoomView(spaceId, conversationId, status, room)
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
