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

/**
 * Records only an admitted irreversible same-target freeze intent. No provider work, SDK drain,
 * completed transfer or new authority is implied. Caller must have durably gated its old SDK.
 */
class FreezeNativeCollaborationLegacyRuntime(
    private val registry: CollaborationSpaceDeviceRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val runtimes: CollaborationLegacyRuntimeRepository,
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
        nativeSessionId: UUID,
        nativeSessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        operationId: UUID,
        targetDeviceId: UUID,
        expectedDeviceVersion: Long,
        fingerprint: String,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        if (expectedDeviceVersion !in 0..999998 || !fingerprint.matches(Regex("[a-f0-9]{64}")))
            return changed()
        val started = clock.instant()
        val key =
            OperationKey(
                "collaboration.legacy_runtime_freeze",
                operationId,
                listOf(
                    spaceId.toString(),
                    provider.serverName,
                    nativeSessionId.toString(),
                    targetDeviceId.toString(),
                    expectedDeviceVersion.toString(),
                    fingerprint,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guarded = identities.lockAccount(actor.accountId, shared = false)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(nativeSessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val access = identities.access(actor.accountId, null)
                if (access is Result.Failed) return access
                val current = spaces.current()
                if (current is Result.Failed) return current
                val now = clock.instant()
                if (now < started || now >= started.plusSeconds(30))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_expired")
                    )
                return admitCollaborationSpaceSession(
                        actor,
                        (access as Result.Success).value,
                        parent,
                        nativeSessionId,
                        nativeSessionVersion,
                        (current as Result.Success).value,
                        client,
                        policy,
                        security,
                        now,
                    )
                    .flatMap { if (it.spaceId == spaceId) Result.Success(it) else denied() }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val selected = selections.selection(spaceId, actor.accountId, provider.serverName)
            if (selected is Result.Failed) return@run selected
            val input = (selected as Result.Success).value ?: return@run denied()
            if (input.selected == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_identity_selection_required")
                )
            val chosen =
                selectCollaborationSpaceIdentity(
                    spaceId,
                    actor.accountId,
                    provider.serverName,
                    input,
                    clock.instant(),
                )
            if (chosen is Result.Failed) return@run chosen
            val identity = (chosen as Result.Success).value
            val bound = registry.findBound(spaceId, actor.accountId, nativeSessionId)
            if (bound is Result.Failed) return@run bound
            if ((bound as Result.Success).value?.id != targetDeviceId) return@run changed()
            val found = registry.metadata(spaceId, actor.accountId, targetDeviceId, lock = true)
            if (found is Result.Failed) return@run found
            val target = (found as Result.Success).value ?: return@run changed()
            val legacy = target.legacy ?: return@run changed()
            fun continuity(
                current: CollaborationSpaceAdmission
            ): Result<CollaborationLegacyRuntimeOwner> {
                val source = legacyParents.find(spaceId, actor.accountId, nativeSessionId, legacy)
                if (source is Result.Failed) return source
                return collaborationLegacyFreezeOwner(
                    identity,
                    target,
                    (source as Result.Success).value,
                    current,
                    expectedDeviceVersion,
                    fingerprint,
                    clock.instant(),
                )
            }
            val matched = continuity((admitted as Result.Success).value)
            if (matched is Result.Failed) return@run matched
            val owner = (matched as Result.Success).value
            val destination = CollaborationLegacyRuntimeTarget(spaceId, target.id)
            val acquired = runtimes.ensure(owner, clock.instant().truncatedTo(ChronoUnit.MICROS))
            if (acquired is Result.Failed) return@run acquired
            val runtime = (acquired as Result.Success).value
            val decision =
                collaborationLegacyFreezeAction(runtime, owner, destination, clock.instant())
            if (decision is Result.Failed) return@run decision
            val action = (decision as Result.Success).value
            fun completion(receipt: MutationReceipt): Result<MutationReceipt> =
                admission().flatMap { current ->
                    continuity(current).flatMap { original ->
                        if (original != owner) changed()
                        else
                            runtimes.find(owner, lock = true).flatMap { frozen ->
                                if (frozen == null) changed()
                                else
                                    collaborationLegacyFreezeAction(
                                            frozen,
                                            owner,
                                            destination,
                                            clock.instant(),
                                        )
                                        .flatMap {
                                            if (it != CollaborationLegacyFreezeAction.REPLAY)
                                                changed()
                                            else admission().map { receipt }
                                        }
                            }
                    }
                }
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (
                    previous.id != owner.deviceId ||
                        previous.version != 1L ||
                        action != CollaborationLegacyFreezeAction.REPLAY
                )
                    return@run changed()
                return@run completion(previous)
            }
            val receipt = MutationReceipt(owner.deviceId, 1)
            val frozen =
                if (action == CollaborationLegacyFreezeAction.REPLAY) Result.Success(Unit)
                else
                    runtimes
                        .freeze(
                            owner,
                            destination,
                            runtime.version,
                            clock.instant().truncatedTo(ChronoUnit.MICROS),
                        )
                        .flatMap { if (it) Result.Success(Unit) else changed() }
            frozen
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    if (action == CollaborationLegacyFreezeAction.REPLAY) Result.Success(Unit)
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "collaboration_legacy_runtime",
                                owner.deviceId,
                                "collaboration.legacy_runtime_frozen",
                                mapOf(
                                    "spaceId" to spaceId.toString(),
                                    "targetDeviceId" to target.id.toString(),
                                ),
                            ),
                        )
                }
                .flatMap { completion(receipt) }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_runtime_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
