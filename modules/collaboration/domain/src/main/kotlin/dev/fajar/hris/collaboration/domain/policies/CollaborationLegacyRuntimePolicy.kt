package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Duration
import java.time.Instant

fun collaborationLegacyRuntimeOwner(admission: CollaborationAdmission) =
    CollaborationLegacyRuntimeOwner(
        admission.companyId,
        admission.deviceId,
        admission.accountId,
        admission.nativeSessionId,
    )

/** Current native/company/client/MFA admission is still owned by the caller. */
fun admitCollaborationLegacyRuntime(
    runtime: CollaborationLegacyRuntime,
    admission: CollaborationAdmission,
    at: Instant,
): Result<Unit> {
    if (
        runtime.owner != collaborationLegacyRuntimeOwner(admission) ||
            runtime.createdAt > at ||
            at < admission.admittedAt ||
            at >= admission.expiresAt
    )
        return changed()
    return when (runtime.phase) {
        CollaborationLegacyRuntimePhase.LEGACY ->
            if (runtime.version == 0L && runtime.frozenAt == null && runtime.target == null)
                Result.Success(Unit)
            else changed()
        CollaborationLegacyRuntimePhase.DRAINING ->
            if (
                runtime.version == 1L &&
                    runtime.frozenAt != null &&
                    runtime.frozenAt >= runtime.createdAt &&
                    runtime.frozenAt <= at &&
                    runtime.target != null
            )
                Result.Failed(
                    Failure(FailureKind.UNAVAILABLE, "collaboration_runtime_transferring")
                )
            else changed()
    }
}

/** Caller holds runtime then exact lease guards when committing business publication. */
fun validateCollaborationLegacyRuntimeLease(
    runtime: CollaborationLegacyRuntime,
    expected: CollaborationLegacyRuntimeLease,
    current: CollaborationLegacyRuntimeLease?,
    admission: CollaborationAdmission,
    at: Instant,
): Result<Unit> =
    admitCollaborationLegacyRuntime(runtime, admission, at).flatMap {
        if (
            expected != current ||
                expected.owner != runtime.owner ||
                expected.runtimeVersion != runtime.version ||
                expected.slot !in 0..7 ||
                expected.releasedAt != null ||
                expected.startedAt < runtime.createdAt ||
                expected.startedAt > at ||
                expected.expiresAt <= at ||
                expected.expiresAt > admission.expiresAt ||
                Duration.between(expected.startedAt, expected.expiresAt) > Duration.ofSeconds(60)
        )
            changed()
        else Result.Success(Unit)
    }

private fun changed() =
    Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_runtime_changed"))
