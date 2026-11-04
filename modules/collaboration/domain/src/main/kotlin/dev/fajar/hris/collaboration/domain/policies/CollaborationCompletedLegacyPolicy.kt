package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Instant

/** Immutable completion is provenance, never a replacement for current native/group admission. */
fun validateCompletedCollaborationLegacyDevice(
    identity: CollaborationSpaceIdentity,
    device: CollaborationSpaceDevice,
    source: CollaborationLegacyParent?,
    completion: CollaborationLegacyRuntimeCompletion?,
    provisioning: CollaborationSpaceProvisioning?,
    admission: CollaborationSpaceAdmission,
    at: Instant,
): Result<Unit> {
    val legacy = device.legacy ?: return changed()
    val fingerprint = device.legacyKeyFingerprint ?: return changed()
    val original = source?.device ?: return changed()
    val key = source.key ?: return changed()
    val done = completion ?: return changed()
    val head = provisioning ?: return changed()
    val outcome = head.lastOutcome ?: return changed()
    val owner =
        CollaborationLegacyRuntimeOwner(
            legacy.companyId,
            legacy.deviceId,
            admission.accountId,
            admission.nativeSessionId,
        )
    if (
        at < admission.admittedAt ||
            at >= admission.expiresAt ||
            identity.spaceId != admission.spaceId ||
            identity.accountId != admission.accountId ||
            device.origin != CollaborationSpaceDeviceOrigin.LEGACY ||
            device.state != CollaborationDeviceState.ACTIVE ||
            device.platform.name != admission.client.platform.name ||
            done.owner != owner ||
            done.target != CollaborationLegacyRuntimeTarget(admission.spaceId, device.id) ||
            done.matrixUserId != device.matrixUserId ||
            done.matrixDeviceId != device.matrixDeviceId ||
            done.fingerprint != fingerprint ||
            done.nativeSessionVersion > admission.nativeSessionVersion ||
            device.version < done.targetDeviceVersion ||
            device.createdAt > done.frozenAt ||
            done.completedAt > at ||
            done.keysObservedAt < done.frozenAt ||
            done.keysObservedAt > done.completedAt ||
            original.position != legacy ||
            original.accountId != admission.accountId ||
            source.nativeSessionId != admission.nativeSessionId ||
            original.matrixUserId != device.matrixUserId ||
            original.matrixDeviceId != device.matrixDeviceId ||
            original.platform != device.platform ||
            original.state != CollaborationDeviceState.REVOKED ||
            original.deviceVersion < done.sourceDeviceVersion ||
            original.deviceRevokedAt != done.completedAt ||
            original.credentialGeneration != done.sourceProviderGeneration ||
            original.credentialRetiredAt != done.completedAt ||
            original.identityObservedAt < original.deviceCreatedAt ||
            original.identityObservedAt > done.frozenAt ||
            source.boundAt < original.deviceCreatedAt ||
            source.boundAt > done.frozenAt ||
            key.fingerprint != fingerprint ||
            key.reservedAt < original.deviceCreatedAt ||
            key.reservedAt > done.keysObservedAt ||
            key.version !in 0..1 ||
            (key.version == 1L) != (key.publishedAt != null) ||
            key.publishedAt?.let { it < key.reservedAt || it > at } == true ||
            head.request.spaceId != admission.spaceId ||
            head.request.accountId != admission.accountId ||
            head.request.deviceId != device.id ||
            head.request.nativeSessionId != admission.nativeSessionId ||
            head.request.nativeSessionVersion != done.nativeSessionVersion ||
            head.request.revision != done.provisioningRevision ||
            head.state != CollaborationSpaceProvisioningState.SUCCEEDED ||
            head.lease != null ||
            outcome.state != CollaborationSpaceProvisioningState.SUCCEEDED ||
            outcome.revision != done.provisioningRevision ||
            outcome.failure != null
    )
        return changed()
    // Group publication timestamps precede the SQL completion timestamp slightly. Compare
    // their independently validated versions; do not invent equality across those clocks.
    return validateExistingCollaborationSpaceDevice(
        identity,
        device,
        admission.nativeSessionId,
        device.platform,
        CollaborationSpaceDeviceOrigin.LEGACY,
        legacy,
        fingerprint,
        at,
    )
}

private fun changed() =
    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_device_migration_required"))
