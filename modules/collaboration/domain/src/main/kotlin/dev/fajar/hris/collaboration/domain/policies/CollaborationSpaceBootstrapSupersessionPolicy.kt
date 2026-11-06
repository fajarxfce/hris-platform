package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrapIntent
import dev.fajar.hris.core.domain.*

/**
 * Issuance must still compare the observed head under its existing transaction guards. Advancing
 * that head fences the original CAS or receipt; V126 forbids deletion and version regression.
 */
fun validateCollaborationSpaceBootstrapSupersession(
    original: CollaborationSpaceGatewayBootstrapIntent,
    replacement: CollaborationSpaceGatewayBootstrapIntent,
): Result<Unit> {
    if (
        original.operationId == replacement.operationId ||
            original.spaceId != replacement.spaceId ||
            original.accountId != replacement.accountId ||
            original.nativeSessionId != replacement.nativeSessionId ||
            original.deviceId != replacement.deviceId ||
            original.client.platform != replacement.client.platform ||
            original.client.platform !in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) ||
            original.client.build !in 1..999999999 ||
            replacement.client.build !in 1..999999999 ||
            original.expectedDeviceVersion !in 0..999999 ||
            replacement.expectedDeviceVersion !in 0..999999 ||
            replacement.expectedDeviceVersion < original.expectedDeviceVersion ||
            (original.expectedGatewayVersion != null &&
                original.expectedGatewayVersion !in 0..999998) ||
            (replacement.expectedGatewayVersion != null &&
                replacement.expectedGatewayVersion !in 0..999998) ||
            (replacement.expectedGatewayVersion ?: -1) < (original.expectedGatewayVersion ?: -1)
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "collaboration_space_bootstrap_replacement_changed")
        )
    return Result.Success(Unit)
}
