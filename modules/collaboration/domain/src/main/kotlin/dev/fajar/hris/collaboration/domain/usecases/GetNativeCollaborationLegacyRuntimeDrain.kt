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

/**
 * Read-only bounded drain metadata under current native admission; it cannot complete a transfer.
 */
class GetNativeCollaborationLegacyRuntimeDrain(
    private val registry: CollaborationSpaceDeviceRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val runtimes: CollaborationLegacyRuntimeRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
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
        targetDeviceId: UUID,
        expectedDeviceVersion: Long,
        fingerprint: String,
    ): Result<CollaborationLegacyRuntimeDrainStatus> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        if (expectedDeviceVersion !in 0..999998 || !fingerprint.matches(Regex("[a-f0-9]{64}")))
            return changed()
        val started = clock.instant()
        return transactions.run(actor) {
            val guarded = identities.lockAccount(actor.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val access = identities.access(actor.accountId, null)
                if (access is Result.Failed) return access
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed")
                    )
                return admitCollaborationSpaceSession(
                        actor,
                        (access as Result.Success).value,
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
            val found = registry.findBound(spaceId, actor.accountId, sessionId)
            if (found is Result.Failed) return@run found
            val target = (found as Result.Success).value ?: return@run changed()
            if (target.id != targetDeviceId) return@run changed()
            val legacy = target.legacy ?: return@run changed()
            fun continuity(
                current: CollaborationSpaceAdmission
            ): Result<CollaborationLegacyRuntimeOwner> =
                legacyParents.find(spaceId, actor.accountId, sessionId, legacy).flatMap { source ->
                    if (target.state == CollaborationDeviceState.ACTIVE) {
                        val owner =
                            CollaborationLegacyRuntimeOwner(
                                legacy.companyId,
                                legacy.deviceId,
                                actor.accountId,
                                sessionId,
                            )
                        val completed = completions.find(owner)
                        if (completed is Result.Failed) return@flatMap completed
                        val done = (completed as Result.Success).value ?: return@flatMap changed()
                        if (
                            done.targetDeviceVersion != expectedDeviceVersion + 1 ||
                                done.fingerprint != fingerprint
                        )
                            return@flatMap changed()
                        val head = provisioning.metadata(spaceId, actor.accountId, target.id)
                        if (head is Result.Failed) return@flatMap head
                        return@flatMap validateCompletedCollaborationLegacyDevice(
                                identity,
                                target,
                                source,
                                done,
                                (head as Result.Success).value,
                                current,
                                clock.instant(),
                            )
                            .map { owner }
                    }
                    collaborationLegacyFreezeOwner(
                        identity,
                        target,
                        source,
                        current,
                        expectedDeviceVersion,
                        fingerprint,
                        clock.instant(),
                    )
                }
            val matched = continuity((admitted as Result.Success).value)
            if (matched is Result.Failed) return@run matched
            val owner = (matched as Result.Success).value
            val foundRuntime = runtimes.find(owner, lock = false)
            if (foundRuntime is Result.Failed) return@run foundRuntime
            val runtime = (foundRuntime as Result.Success).value ?: return@run changed()
            // The shared account guard excludes new freeze/activation. Exact cleanup can only
            // reduce the live count, so a concurrent release makes this observation conservative.
            val slots = runtimes.slots(owner)
            if (slots is Result.Failed) return@run slots
            admission().flatMap { current ->
                continuity(current).flatMap { original ->
                    if (original != owner) changed()
                    else
                        admission().flatMap { last ->
                            collaborationLegacyRuntimeDrainStatus(
                                runtime,
                                owner,
                                CollaborationLegacyRuntimeTarget(spaceId, targetDeviceId),
                                (slots as Result.Success).value,
                                last.admittedAt,
                            )
                        }
                }
            }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_runtime_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
