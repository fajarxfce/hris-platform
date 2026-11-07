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

/** Reads an actor-owned original receipt. Never admits its old proof or changes provider work. */
class ReadNativeCollaborationSpaceRoomReceipt(
    private val operations: OperationRepository,
    private val rooms: CollaborationSpaceRoomRepository,
    private val conversations: CollaborationSpaceConversationRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun <T : CollaborationSpaceRoomReceiptDescriptor> execute(
        actor: Actor,
        currentParentId: UUID,
        currentParentVersion: Long,
        currentClient: ClientVersion?,
        original: T,
    ): Result<CollaborationSpaceRoomReceiptObservation<T>> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(currentClient, policy)
        if (available is Result.Failed) return available
        if (
            currentParentId == UUID(0, 0) ||
                currentParentVersion < 0 ||
                !validCollaborationSpaceRoomReceiptDescriptor(original)
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_room_operation")
            )
        val started = clock.instant()
        return transactions.run(actor) {
            // Match the writer's lock order. Do not disclose even a failed lookup before admission.
            val receipt =
                operations.lockAndReplay(actor, collaborationSpaceRoomReceiptKey(original))
            val roomGuard = rooms.lock(original.spaceId, original.conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            val found =
                conversations.find(original.spaceId, actor.accountId, original.conversationId)
            if (found is Result.Failed) return@run found
            val conversation =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "collaboration_conversation_not_found")
                    )
            val pair = listOf(conversation.firstAccountId, conversation.secondAccountId)
            if (
                conversation.spaceId != original.spaceId ||
                    conversation.id != original.conversationId ||
                    actor.accountId !in pair ||
                    pair.distinct().size != 2
            )
                return@run denied()
            for (account in pair.sorted()) {
                val guard = identities.lockAccount(account, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val locked = sessions.lock(currentParentId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun nativeAdmission(): Result<Unit> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val now = clock.instant()
                if (now < started || now >= started.plusSeconds(30)) return changed()
                val native =
                    admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        currentParentId,
                        currentParentVersion,
                        (current as Result.Success).value,
                        currentClient,
                        policy,
                        security,
                        now,
                    )
                if (native is Result.Failed) return native
                if ((native as Result.Success).value.spaceId != original.spaceId) return denied()
                return Result.Success(Unit)
            }
            fun admission(): Result<Unit> {
                val before = nativeAdmission()
                if (before is Result.Failed) return before
                var peers: Result<Unit> = Result.Success(Unit)
                for (account in pair) {
                    val member = conversations.member(original.spaceId, account)
                    if (member is Result.Failed) {
                        peers = member
                        break
                    }
                    val value = (member as Result.Success).value
                    if (
                        value == null ||
                            value.spaceId != original.spaceId ||
                            value.accountId != account
                    ) {
                        peers =
                            Result.Failed(
                                Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                            )
                        break
                    }
                }
                return nativeAdmission().flatMap { peers }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val outcome =
                if (original.expectedAttempt?.accountId?.let { it !in pair } == true) {
                    Result.Failed(
                        Failure(FailureKind.VALIDATION, "invalid_collaboration_room_operation")
                    )
                } else
                    receipt.flatMap { value ->
                        if (
                            value != null &&
                                (value.id != original.conversationId || value.version !in 0..999999)
                        )
                            changed()
                        else
                            Result.Success(
                                CollaborationSpaceRoomReceiptObservation(original, value)
                            )
                    }
            admission().flatMap { outcome }
        }
    }

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_room_provisioning_changed"))
}
