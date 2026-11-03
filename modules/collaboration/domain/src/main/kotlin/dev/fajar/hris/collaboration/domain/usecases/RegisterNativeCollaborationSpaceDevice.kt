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

/** Atomically registers identity intent, its native binding and bounded provider work. */
class RegisterNativeCollaborationSpaceDevice(
    private val registry: CollaborationSpaceDeviceRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
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
        nativeSessionId: UUID,
        nativeSessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
        operationId: UUID,
        platform: CollaborationPlatform,
        label: String,
        origin: CollaborationSpaceDeviceOrigin,
        legacy: CollaborationLegacyDevicePosition? = null,
        legacyKeyFingerprint: String? = null,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        val request =
            validateCollaborationSpaceDeviceRequest(
                origin,
                legacy,
                legacyKeyFingerprint,
                platform,
                label,
                client,
            )
        if (request is Result.Failed) return request
        val name = (request as Result.Success).value
        val started = clock.instant()
        val key =
            OperationKey(
                "collaboration.space_device_register",
                operationId,
                listOf(
                    spaceId.toString(),
                    provider.serverName,
                    nativeSessionId.toString(),
                    platform.name,
                    name,
                    origin.name,
                    legacy?.companyId?.toString() ?: "",
                    legacy?.deviceId?.toString() ?: "",
                    legacyKeyFingerprint ?: "",
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = identities.lockAccount(actor.accountId, shared = false)
            if (guard is Result.Failed) return@run guard
            val locked = sessions.lock(nativeSessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val now = clock.instant()
                if (now < started || !now.isBefore(started.plusSeconds(30)))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_expired")
                    )
                return admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        nativeSessionId,
                        nativeSessionVersion,
                        (current as Result.Success).value,
                        client,
                        policy,
                        security,
                        now,
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
            val read = selections.selection(spaceId, actor.accountId, provider.serverName)
            if (read is Result.Failed) return@run read
            val input =
                (read as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                    )
            if (input.selected == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "collaboration_identity_selection_required")
                )
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
            fun continuity(): Result<Unit> {
                if (legacy == null) return Result.Success(Unit)
                val source = legacyParents.find(spaceId, actor.accountId, nativeSessionId, legacy)
                if (source is Result.Failed) return source
                return validateCollaborationSpaceLegacyDevice(
                    identity,
                    (source as Result.Success).value,
                    nativeSessionId,
                    platform,
                    legacy,
                    requireNotNull(legacyKeyFingerprint),
                    clock.instant(),
                )
            }
            fun completion(): Result<Unit> =
                admission().flatMap { continuity() }.flatMap { admission().map { Unit } }
            val retained = continuity()
            if (retained is Result.Failed) return@run retained
            val found = registry.findBound(spaceId, actor.accountId, nativeSessionId)
            if (found is Result.Failed) return@run found
            val existing = (found as Result.Success).value
            val previous = (replay as Result.Success).value
            if (existing != null) {
                val same =
                    validateExistingCollaborationSpaceDevice(
                        identity,
                        existing,
                        nativeSessionId,
                        platform,
                        origin,
                        legacy,
                        legacyKeyFingerprint,
                        clock.instant(),
                    )
                if (same is Result.Failed) return@run same
                if (previous != null) {
                    if (previous.id != existing.id || previous.version !in 0..existing.version)
                        return@run changed()
                    return@run completion().map { previous }
                }
                val receipt = MutationReceipt(existing.id, existing.version)
                return@run operations.record(actor, key, receipt).flatMap {
                    completion().map { receipt }
                }
            }
            if (previous != null) return@run changed()
            val count =
                registry.otherActiveParents(
                    spaceId,
                    actor.accountId,
                    nativeSessionId,
                    clock.instant(),
                )
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 5)
                return@run Result.Failed(
                    Failure(
                        FailureKind.CONFLICT,
                        "collaboration_device_limit",
                        parameters = mapOf("maximum" to "5"),
                    )
                )
            val refreshed = admission()
            if (refreshed is Result.Failed) return@run refreshed
            val current = (refreshed as Result.Success).value
            val id = UUID.randomUUID()
            val at = current.admittedAt.truncatedTo(ChronoUnit.MICROS)
            val deadline = current.expiresAt.truncatedTo(ChronoUnit.MICROS)
            if (deadline <= at)
                return@run Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_admission_expired")
                )
            val work =
                CollaborationSpaceProvisioningRequest(
                    spaceId,
                    actor.accountId,
                    id,
                    nativeSessionId,
                    0,
                    current.nativeSessionVersion,
                    current.credentialVersion,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    actor.correlationId,
                    current.client,
                    at,
                    deadline,
                )
            val device =
                CollaborationSpaceDevice(
                    id,
                    spaceId,
                    actor.accountId,
                    identity.matrixUserId,
                    legacy?.let { collaborationMatrixDeviceId(it.deviceId) }
                        ?: "HRISG_" + id.toString().replace("-", ""),
                    origin,
                    legacy,
                    legacyKeyFingerprint,
                    nativeSessionId,
                    platform,
                    name,
                    CollaborationDeviceState.PROVISIONING,
                    0,
                    at,
                    at,
                    null,
                )
            val receipt = MutationReceipt(id, 0)
            registry
                .register(device)
                .flatMap { provisioning.enqueue(work) }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_space_device",
                            id,
                            "collaboration.space_device_registered",
                            mapOf(
                                "spaceId" to spaceId.toString(),
                                "origin" to origin.name,
                                "platform" to platform.name,
                                "version" to "0",
                                "provisioningRevision" to "0",
                            ),
                        ),
                    )
                }
                .flatMap { completion().map { receipt } }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_device_changed"))
}
