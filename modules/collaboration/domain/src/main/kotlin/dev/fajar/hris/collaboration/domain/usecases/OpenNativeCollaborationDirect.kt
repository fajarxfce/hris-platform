package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationDirectRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Opens one canonical pair, including competing requests initiated by opposite participants. */
class OpenNativeCollaborationDirect(
    private val conversations: CollaborationDirectRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        operationId: UUID,
        peerId: UUID,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (
            client == null ||
                client.platform !in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) ||
                client.build <= 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
        if (peerId == actor.accountId)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_peer"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        // java.util.UUID.compareTo uses signed longs; PostgreSQL orders unsigned bytes.
        val pair = listOf(actor.accountId, peerId).sortedBy(UUID::toString)
        val key = OperationKey("collaboration.direct_open", operationId, listOf(peerId.toString()))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val pairGuard = conversations.lockPair(company, pair[0], pair[1])
            if (pairGuard is Result.Failed) return@run pairGuard
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            // Shared account guards follow the existing identity module ordering,
            // independently of the unsigned ordering used for the stored pair.
            for (account in pair.sorted()) {
                val ag = identities.lockAccount(account, shared = true)
                if (ag is Result.Failed) return@run ag
            }
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            if (parent?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            fun admission(): Result<Unit> =
                policies.effective(company, clock.instant()).flatMap {
                    val at = clock.instant()
                    nativeCollaborationClientDeadline(it, client, at).flatMap {
                        collaborationParentDeadline(
                                actor,
                                access,
                                parent,
                                sessionVersion,
                                at,
                                security,
                            )
                            .map { Unit }
                    }
                }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            // Discovery is only a hint. Read this peer's live authority under its account guard.
            val selected = identities.access(peerId, company)
            if (selected is Result.Failed) return@run selected
            val peer = (selected as Result.Success).value
            if (
                peer == null ||
                    peer.account.id != peerId ||
                    !peer.account.active ||
                    !peer.membershipActive ||
                    !peer.companyActive ||
                    COLLABORATION_CHAT_PERMISSION !in peer.permissions
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable")
                )
            val found = conversations.findPair(company, pair[0], pair[1])
            if (found is Result.Failed) return@run found
            val current = (found as Result.Success).value
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (current?.id != previous.id)
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
            val conversation =
                CollaborationDirectConversation(
                    UUID.randomUUID(),
                    company,
                    pair[0],
                    pair[1],
                    actor.accountId,
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                )
            val receipt = MutationReceipt(conversation.id, conversation.version)
            conversations
                .create(conversation)
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_direct_conversation",
                            conversation.id,
                            "collaboration.direct_opened",
                            mapOf("version" to "0"),
                        ),
                    )
                }
                .flatMap { admission().map { receipt } }
        }
    }
}
