package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Explicitly replaces one ended request; replay never restarts provider work. */
class RetryNativeCollaborationSpaceProvisioning(
    private val registry: CollaborationSpaceDeviceRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val completions: CollaborationLegacyRuntimeCompletionRepository,
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
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedProvisioningRevision: Long,
    ): Result<MutationReceipt> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val available = validateCollaborationSpaceClient(client, policy)
        if (available is Result.Failed) return available
        if (expectedDeviceVersion !in 0..999998 || expectedProvisioningRevision !in 0..999998)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_provisioning_version")
            )
        val started = clock.instant()
        val key =
            OperationKey(
                "collaboration.space_provisioning_retry",
                operationId,
                listOf(
                    spaceId.toString(),
                    provider.serverName,
                    nativeSessionId.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedProvisioningRevision.toString(),
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
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val now = clock.instant()
                if (now < started || now >= started.plusSeconds(30)) return expired()
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
                    .flatMap { if (it.spaceId == spaceId) Result.Success(it) else denied() }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val readIdentity = selections.selection(spaceId, actor.accountId, provider.serverName)
            if (readIdentity is Result.Failed) return@run readIdentity
            val input = (readIdentity as Result.Success).value ?: return@run denied()
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
            val readDevice = registry.metadata(spaceId, actor.accountId, deviceId, lock = true)
            if (readDevice is Result.Failed) return@run readDevice
            val device = (readDevice as Result.Success).value ?: return@run denied()
            val readHead = provisioning.metadata(spaceId, actor.accountId, deviceId, lock = true)
            if (readHead is Result.Failed) return@run readHead
            val head = (readHead as Result.Success).value ?: return@run changed()
            fun continuity(current: CollaborationSpaceAdmission): Result<Unit> {
                val found = registry.findBound(spaceId, actor.accountId, nativeSessionId)
                if (found is Result.Failed) return found
                if ((found as Result.Success).value != device) return changed()
                if (client?.platform?.name != device.platform.name) return denied()
                val valid =
                    validateExistingCollaborationSpaceDevice(
                        identity,
                        device,
                        nativeSessionId,
                        device.platform,
                        device.origin,
                        device.legacy,
                        device.legacyKeyFingerprint,
                        clock.instant(),
                    )
                if (valid is Result.Failed) return valid
                val legacy = device.legacy ?: return Result.Success(Unit)
                val source = legacyParents.find(spaceId, actor.accountId, nativeSessionId, legacy)
                if (source is Result.Failed) return source
                if (device.state == CollaborationDeviceState.ACTIVE) {
                    val completed =
                        completions.find(
                            CollaborationLegacyRuntimeOwner(
                                legacy.companyId,
                                legacy.deviceId,
                                actor.accountId,
                                nativeSessionId,
                            )
                        )
                    if (completed is Result.Failed) return completed
                    return validateCompletedCollaborationLegacyDevice(
                        identity,
                        device,
                        (source as Result.Success).value,
                        (completed as Result.Success).value,
                        head,
                        current,
                        clock.instant(),
                    )
                }
                return validateCollaborationSpaceLegacyDevice(
                    identity,
                    (source as Result.Success).value,
                    nativeSessionId,
                    device.platform,
                    legacy,
                    requireNotNull(device.legacyKeyFingerprint),
                    clock.instant(),
                )
            }
            fun completion(): Result<Unit> =
                admission().flatMap { continuity(it) }.flatMap { admission().map { Unit } }
            val retained = continuity((admitted as Result.Success).value)
            if (retained is Result.Failed) return@run retained
            val priorRequest = head.request
            if (
                priorRequest.nativeSessionId != nativeSessionId ||
                    priorRequest.requestedAt > clock.instant()
            )
                return@run changed()
            val previous = (replay as Result.Success).value
            if (previous != null) {
                if (
                    previous.id != deviceId ||
                        previous.version != expectedProvisioningRevision + 1 ||
                        previous.version > priorRequest.revision
                )
                    return@run changed()
                return@run completion().map { previous }
            }
            if (
                device.state != CollaborationDeviceState.PROVISIONING ||
                    device.version != expectedDeviceVersion ||
                    priorRequest.revision != expectedProvisioningRevision
            )
                return@run changed()
            val pending =
                head.state in
                    setOf(
                        CollaborationSpaceProvisioningState.QUEUED,
                        CollaborationSpaceProvisioningState.RUNNING,
                    )
            if (
                head.state == CollaborationSpaceProvisioningState.SUCCEEDED ||
                    (pending && priorRequest.expiresAt > clock.instant())
            )
                return@run changed()
            val now = clock.instant()
            val earliest =
                priorRequest.requestedAt.plusSeconds(COLLABORATION_PROVISIONING_RETRY_SECONDS)
            if (now < earliest)
                return@run Result.Failed(
                    Failure(
                        FailureKind.RATE_LIMITED,
                        "collaboration_provisioning_retry_too_soon",
                        parameters =
                            mapOf(
                                "retryAfterSeconds" to
                                    ((Duration.between(now, earliest).toMillis() + 999) / 1000)
                                        .coerceAtLeast(1)
                                        .toString()
                            ),
                    )
                )
            if (pending) {
                val cancelled =
                    provisioning.cancel(spaceId, actor.accountId, deviceId, priorRequest.revision)
                if (cancelled is Result.Failed) return@run cancelled
                if (!(cancelled as Result.Success).value) return@run changed()
            }
            val refreshed = admission()
            if (refreshed is Result.Failed) return@run refreshed
            val current = (refreshed as Result.Success).value
            val at = current.admittedAt.truncatedTo(ChronoUnit.MICROS)
            val deadline = current.expiresAt.truncatedTo(ChronoUnit.MICROS)
            if (deadline <= at || head.lastOutcome?.at?.let { it > at } == true)
                return@run expired()
            val request =
                CollaborationSpaceProvisioningRequest(
                    spaceId,
                    actor.accountId,
                    deviceId,
                    nativeSessionId,
                    priorRequest.revision + 1,
                    current.nativeSessionVersion,
                    current.credentialVersion,
                    actor.authenticatedAt,
                    actor.mfaVerifiedAt,
                    actor.correlationId,
                    current.client,
                    at,
                    deadline,
                )
            val receipt = MutationReceipt(deviceId, request.revision)
            provisioning
                .retry(request, priorRequest.revision)
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_space_device",
                            deviceId,
                            "collaboration.space_provisioning_retried",
                            mapOf(
                                "spaceId" to spaceId.toString(),
                                "deviceVersion" to device.version.toString(),
                                "provisioningRevision" to request.revision.toString(),
                                "replacedState" to head.state.name,
                            ),
                        ),
                    )
                }
                .flatMap { completion().map { receipt } }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_provisioning_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun expired() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_expired"))
}
