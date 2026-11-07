package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Checks published original keys before local staging; no old SDK or server transition. */
class VerifyNativeCollaborationLegacyPublication(
    private val registry: CollaborationSpaceDeviceRepository,
    private val legacyParents: CollaborationLegacyParentRepository,
    private val selections: CollaborationSpaceIdentityRepository,
    private val runtimes: CollaborationLegacyRuntimeRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val matrix: CollaborationSpaceMatrixRepository,
    private val keys: CollaborationMatrixKeyRepository,
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
    ): Result<CollaborationLegacyPublication> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (!provider.enabled || !validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        if (expectedDeviceVersion !in 0..999998 || !fingerprint.matches(Regex("[a-f0-9]{64}")))
            return changed()
        val request =
            Request(
                actor,
                sessionId,
                sessionVersion,
                spaceId,
                requireNotNull(client),
                targetDeviceId,
                expectedDeviceVersion,
                fingerprint,
                clock.instant(),
            )
        val captured = transactions.run(actor) { observe(request) }
        if (captured is Result.Failed) return captured
        val original = (captured as Result.Success).value
        // Never log out a losing same-device candidate: it would remove winning keys/tokens.
        val candidate =
            matrix.login(
                CollaborationSpaceMatrixLogin(
                    spaceId,
                    actor.accountId,
                    targetDeviceId,
                    original.identity.legacy?.companyId,
                    original.target.legacy,
                    original.admission.admittedAt,
                    original.deadline,
                )
            )
        checkInterrupted()
        val afterLogin = reobserve(request, original)
        if (afterLogin is Result.Failed) return afterLogin
        if (candidate is Result.Failed) return candidate
        val credential = (candidate as Result.Success).value
        val at = clock.instant()
        if (
            credential.userId != original.target.matrixUserId ||
                credential.deviceId != original.target.matrixDeviceId ||
                credential.accessToken.length !in 1..2048 ||
                credential.accessToken.any { it.code !in 33..126 } ||
                credential.expiresAt <= at ||
                credential.expiresAt > at.plusSeconds(600)
        )
            return invalidKeys()
        val deadline =
            minOf(
                original.deadline,
                (afterLogin as Result.Success).value.deadline,
                credential.expiresAt,
            )
        val published =
            keys.ownDeviceKeys(
                credential.userId,
                credential.deviceId,
                credential.accessToken,
                deadline,
            )
        val observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
        checkInterrupted()
        val afterKeys = reobserve(request, original)
        if (afterKeys is Result.Failed) return afterKeys
        if (published is Result.Failed) return published
        val currentKeys = (published as Result.Success).value ?: return invalidKeys()
        val verified =
            validateCollaborationDeviceKeys(currentKeys, credential.userId, credential.deviceId)
        if (verified is Result.Failed || (verified as Result.Success).value != fingerprint)
            return invalidKeys()
        // Signature verification also consumes time. Finish through fresh admission and guards.
        return transactions.run(actor) {
            observe(request).flatMap { current ->
                consistent(original, current).flatMap {
                    val now = clock.instant()
                    val expiresAt = minOf(deadline, current.deadline)
                    if (now < observedAt || now >= expiresAt) changed()
                    else
                        Result.Success(
                            CollaborationLegacyPublication(
                                spaceId,
                                targetDeviceId,
                                expectedDeviceVersion,
                                requireNotNull(current.target.legacy),
                                fingerprint,
                                observedAt,
                                expiresAt,
                            )
                        )
                }
            }
        }
    }

    private fun observe(request: Request): Result<Observation> {
        checkInterrupted()
        val guarded = identities.lockAccount(request.actor.accountId, shared = true)
        if (guarded is Result.Failed) return guarded
        val locked = sessions.lock(request.sessionId, request.actor.accountId)
        if (locked is Result.Failed) return locked
        val parent = (locked as Result.Success).value
        fun admission(): Result<CollaborationSpaceAdmission> {
            val access = identities.access(request.actor.accountId, null)
            if (access is Result.Failed) return access
            val current = spaces.current()
            if (current is Result.Failed) return current
            val now = clock.instant()
            if (now < request.startedAt || now >= request.startedAt.plusSeconds(30))
                return changed()
            return admitCollaborationSpaceSession(
                    request.actor,
                    (access as Result.Success).value,
                    parent,
                    request.sessionId,
                    request.sessionVersion,
                    (current as Result.Success).value,
                    request.client,
                    policy,
                    security,
                    now,
                )
                .flatMap { if (it.spaceId == request.spaceId) Result.Success(it) else denied() }
        }
        val admitted = admission()
        if (admitted is Result.Failed) return admitted
        val selected =
            selections.selection(request.spaceId, request.actor.accountId, provider.serverName)
        if (selected is Result.Failed) return selected
        val input = (selected as Result.Success).value ?: return denied()
        if (input.selected == null) return changed()
        val chosen =
            selectCollaborationSpaceIdentity(
                request.spaceId,
                request.actor.accountId,
                provider.serverName,
                input,
                clock.instant(),
            )
        if (chosen is Result.Failed) return chosen
        val identity = (chosen as Result.Success).value
        val bound = registry.findBound(request.spaceId, request.actor.accountId, request.sessionId)
        if (bound is Result.Failed) return bound
        val target = (bound as Result.Success).value ?: return changed()
        if (target.id != request.targetDeviceId) return changed()
        val legacy = target.legacy ?: return changed()
        val readSource =
            legacyParents.find(request.spaceId, request.actor.accountId, request.sessionId, legacy)
        if (readSource is Result.Failed) return readSource
        val source = (readSource as Result.Success).value ?: return changed()
        val refreshed = admission()
        if (refreshed is Result.Failed) return refreshed
        val currentAdmission = (refreshed as Result.Success).value
        val matched =
            collaborationLegacyFreezeOwner(
                identity,
                target,
                source,
                currentAdmission,
                request.expectedDeviceVersion,
                request.fingerprint,
                clock.instant(),
            )
        if (matched is Result.Failed) return matched
        val owner = (matched as Result.Success).value
        val readRuntime = runtimes.find(owner, lock = false)
        if (readRuntime is Result.Failed) return readRuntime
        val runtime = (readRuntime as Result.Success).value
        if (runtime != null) {
            val action =
                collaborationLegacyFreezeAction(
                    runtime,
                    owner,
                    CollaborationLegacyRuntimeTarget(request.spaceId, target.id),
                    clock.instant(),
                )
            if (action is Result.Failed) return action
        }
        return admission().map {
            Observation(
                identity,
                target,
                source,
                runtime,
                it,
                minOf(it.expiresAt, request.startedAt.plusSeconds(30)),
            )
        }
    }

    private fun reobserve(request: Request, original: Observation): Result<Observation> =
        transactions.run(request.actor) {
            observe(request).flatMap { current -> consistent(original, current).map { current } }
        }

    private fun consistent(original: Observation, current: Observation): Result<Unit> {
        val at = clock.instant()
        return if (
            original.identity != current.identity ||
                original.target != current.target ||
                original.source != current.source ||
                original.runtime != current.runtime ||
                at < original.admission.admittedAt ||
                at < current.admission.admittedAt ||
                at >= minOf(original.deadline, current.deadline)
        )
            changed()
        else Result.Success(Unit)
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Collaboration publication verification interrupted")
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun invalidKeys() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private data class Request(
        val actor: Actor,
        val sessionId: UUID,
        val sessionVersion: Long,
        val spaceId: UUID,
        val client: ClientVersion,
        val targetDeviceId: UUID,
        val expectedDeviceVersion: Long,
        val fingerprint: String,
        val startedAt: Instant,
    )

    private data class Observation(
        val identity: CollaborationSpaceIdentity,
        val target: CollaborationSpaceDevice,
        val source: CollaborationLegacyParent,
        val runtime: CollaborationLegacyRuntime?,
        val admission: CollaborationSpaceAdmission,
        val deadline: Instant,
    )
}
