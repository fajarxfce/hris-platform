package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant

/**
 * Public metadata only; caller must separately validate canonical identity and retained completion.
 */
fun validateCollaborationSpaceGatewayProvider(
    state: CollaborationSpaceProviderState?,
    device: CollaborationSpaceDevice,
    admission: CollaborationSpaceAdmission,
    at: Instant,
): Result<Unit> {
    if (
        state == null ||
            state.spaceId != admission.spaceId ||
            state.accountId != admission.accountId ||
            state.deviceId != device.id ||
            state.nativeSessionId != admission.nativeSessionId ||
            state.matrixUserId != device.matrixUserId ||
            state.matrixDeviceId != device.matrixDeviceId ||
            state.generation !in 0..999999 ||
            state.clearedAt != null ||
            state.createdAt < device.createdAt ||
            state.updatedAt < state.createdAt ||
            state.updatedAt > at ||
            state.expiresAt <= at ||
            state.expiresAt <= state.updatedAt ||
            at < admission.admittedAt ||
            at >= admission.expiresAt
    )
        return collaborationSpaceGatewayAuthenticationRequired()
    return Result.Success(Unit)
}
