package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Durable pair intent only. Current room/device authority is separately required for content. */
class OpenNativeCollaborationSpaceDirect(
    private val conversations: CollaborationSpaceConversationRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
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
        operationId: UUID,
        peerId: UUID,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (peerId == actor.accountId || peerId == UUID(0, 0))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_peer"))
        if (spaceId == UUID(0, 0) || operationId == UUID(0, 0))
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_conversation_query")
            )
        val pair = listOf(actor.accountId, peerId).sortedBy(UUID::toString)
        val key =
            OperationKey(
                "collaboration.space_direct_open",
                operationId,
                listOf(spaceId.toString(), peerId.toString()),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val pairGuard = conversations.lockPair(spaceId, pair[0], pair[1])
            if (pairGuard is Result.Failed) return@run pairGuard
            // Stored pair uses unsigned order; account guards follow the identity module's
            // signed UUID order. Never acquire company/member guards after these guards.
            for (account in pair.sorted()) {
                val guard = identities.lockAccount(account, shared = true)
                if (guard is Result.Failed) return@run guard
            }
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun nativeAdmission(): Result<Unit> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val live =
                    admitCollaborationSpaceSession(
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
                if (live is Result.Failed) return live
                if ((live as Result.Success).value.spaceId != spaceId)
                    return Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                    )
                return Result.Success(Unit)
            }
            fun admission(): Result<Unit> {
                val live = nativeAdmission()
                if (live is Result.Failed) return live
                for (account in pair) {
                    val member = conversations.member(spaceId, account)
                    if (member is Result.Failed) return member
                    val value = (member as Result.Success).value
                    if (value == null || value.spaceId != spaceId || value.accountId != account)
                        return Result.Failed(
                            Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                        )
                }
                return nativeAdmission()
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val found = conversations.findPair(spaceId, pair[0], pair[1])
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (
                    current == null ||
                        current.id != previous.id ||
                        current.version != previous.version
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_conversation_changed")
                    )
                return@run admission().map { previous }
            }
            if (current != null) {
                val receipt = MutationReceipt(current.id, current.version)
                return@run operations.record(actor, key, receipt).flatMap {
                    admission().map { receipt }
                }
            }
            val value =
                CollaborationSpaceDirectConversation(
                    UUID.randomUUID(),
                    spaceId,
                    pair[0],
                    pair[1],
                    actor.accountId,
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                )
            val receipt = MutationReceipt(value.id, value.version)
            conversations
                .create(value)
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_space_direct_conversation",
                            value.id,
                            "collaboration.space_direct_opened",
                            mapOf("spaceId" to spaceId.toString(), "version" to "0"),
                        ),
                    )
                }
                .flatMap { admission().map { receipt } }
        }
    }
}
