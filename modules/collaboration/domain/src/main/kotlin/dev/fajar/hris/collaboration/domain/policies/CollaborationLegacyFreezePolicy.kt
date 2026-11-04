package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant

/** Caller already holds current group/native admission and the selected canonical identity. */
fun collaborationLegacyFreezeOwner(
    identity: CollaborationSpaceIdentity,
    target: CollaborationSpaceDevice,
    source: CollaborationLegacyParent?,
    admission: CollaborationSpaceAdmission,
    expectedDeviceVersion: Long,
    fingerprint: String,
    at: Instant,
): Result<CollaborationLegacyRuntimeOwner> {
    val legacy = target.legacy ?: return changed()
    if (
        at < admission.admittedAt ||
            at >= admission.expiresAt ||
            identity.spaceId != admission.spaceId ||
            identity.accountId != admission.accountId ||
            target.origin != CollaborationSpaceDeviceOrigin.LEGACY ||
            target.state != CollaborationDeviceState.PROVISIONING ||
            target.version != expectedDeviceVersion ||
            expectedDeviceVersion !in 0..999998 ||
            target.platform.name != admission.client.platform.name ||
            admission.client.build <= 0 ||
            fingerprint.matches(Regex("[a-f0-9]{64}")) != true
    )
        return changed()
    return validateExistingCollaborationSpaceDevice(
            identity,
            target,
            admission.nativeSessionId,
            target.platform,
            CollaborationSpaceDeviceOrigin.LEGACY,
            legacy,
            fingerprint,
            at,
        )
        .flatMap {
            validateCollaborationSpaceLegacyDevice(
                identity,
                source,
                admission.nativeSessionId,
                target.platform,
                legacy,
                fingerprint,
                at,
            )
        }
        .map {
            CollaborationLegacyRuntimeOwner(
                legacy.companyId,
                legacy.deviceId,
                admission.accountId,
                admission.nativeSessionId,
            )
        }
}

/**
 * Same-target replay needs current caller admission too. This function cannot unfreeze/retarget.
 */
fun collaborationLegacyFreezeAction(
    runtime: CollaborationLegacyRuntime,
    owner: CollaborationLegacyRuntimeOwner,
    target: CollaborationLegacyRuntimeTarget,
    at: Instant,
): Result<CollaborationLegacyFreezeAction> {
    if (runtime.owner != owner || runtime.createdAt > at) return changed()
    return when (runtime.phase) {
        CollaborationLegacyRuntimePhase.LEGACY ->
            if (runtime.version == 0L && runtime.frozenAt == null && runtime.target == null)
                Result.Success(CollaborationLegacyFreezeAction.FREEZE)
            else changed()
        CollaborationLegacyRuntimePhase.DRAINING ->
            if (
                runtime.version == 1L &&
                    runtime.target == target &&
                    runtime.frozenAt != null &&
                    runtime.frozenAt >= runtime.createdAt &&
                    runtime.frozenAt <= at
            )
                Result.Success(CollaborationLegacyFreezeAction.REPLAY)
            else changed()
    }
}

private fun changed() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_runtime_changed"))
