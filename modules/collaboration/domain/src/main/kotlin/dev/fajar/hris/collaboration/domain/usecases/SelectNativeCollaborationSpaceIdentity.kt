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

/**
 * Selects provider-user intent only. Provisioning, device adoption and room access are separate.
 */
class SelectNativeCollaborationSpaceIdentity(
    private val selections: CollaborationSpaceIdentityRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
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
        operationId: UUID,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        val key =
            OperationKey(
                "collaboration.space_identity_select",
                operationId,
                listOf(spaceId.toString(), provider.serverName),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            // Serializes first selection with other account/device credential changes.
            // Never acquire company/member guards after these account/parent guards.
            val guard = identities.lockAccount(actor.accountId, shared = false)
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
            val found = selections.selection(spaceId, actor.accountId, provider.serverName)
            if (found is Result.Failed) return@run found
            val input = (found as Result.Success).value
            val selected =
                selectCollaborationSpaceIdentity(
                    spaceId,
                    actor.accountId,
                    provider.serverName,
                    input,
                    clock.instant(),
                )
            if (selected is Result.Failed) return@run selected
            val identity = (selected as Result.Success).value
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (
                    input?.selected == null ||
                        previous.id != identity.accountId ||
                        previous.version != identity.version
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "collaboration_identity_changed")
                    )
                return@run admission().map { previous }
            }
            val fresh = input?.selected == null
            if (fresh) {
                val created = selections.create(identity)
                if (created is Result.Failed) return@run created
            }
            val receipt = MutationReceipt(identity.accountId, identity.version)
            operations
                .record(actor, key, receipt)
                .flatMap {
                    if (fresh)
                        journal.record(
                            actor,
                            ChangeRecord(
                                "collaboration_space_identity",
                                identity.accountId,
                                "collaboration.space_identity_selected",
                                mapOf(
                                    "spaceId" to spaceId.toString(),
                                    "origin" to identity.origin.name,
                                    "version" to identity.version.toString(),
                                ),
                            ),
                        )
                    else Result.Success(Unit)
                }
                .flatMap { admission().map { receipt } }
        }
    }
}
