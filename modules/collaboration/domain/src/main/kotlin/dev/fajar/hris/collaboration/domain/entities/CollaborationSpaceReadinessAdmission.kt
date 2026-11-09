package dev.fajar.hris.collaboration.domain.entities

import dev.fajar.hris.identity.domain.entities.AccountAccess
import dev.fajar.hris.identity.domain.entities.NativeSession
import java.time.Instant
import java.util.UUID

/** Current repository metadata acquired under the target's guards; no credential or FCM token. */
data class CollaborationSpaceReadinessSnapshot(
    val currentSpaceId: UUID?,
    val access: AccountAccess?,
    val parent: NativeSession?,
    val device: CollaborationSpaceDevice?,
    val bound: CollaborationSpaceDevice?,
    val gateway: CollaborationSpaceGatewaySession?,
    val selection: CollaborationSpaceIdentityInput?,
    val keys: CollaborationSpaceKeyPublication?,
    val provider: CollaborationSpaceProviderState?,
    val legacyParent: CollaborationLegacyParent? = null,
    val completion: CollaborationLegacyRuntimeCompletion? = null,
    val provisioning: CollaborationSpaceProvisioning? = null,
) {
    override fun toString() = "CollaborationSpaceReadinessSnapshot(<metadata>)"
}

/**
 * Authority for one bounded opaque wake; never message content, interactive access or key creation.
 */
data class CollaborationSpaceReadinessAdmission(
    val identity: CollaborationSpaceIdentity,
    val owner: CollaborationSpaceHistoryOwner,
    val deviceVersion: Long,
    val nativeSessionVersion: Long,
    val credentialVersion: Long,
    val gateway: CollaborationSpaceGatewaySession,
    val keys: CollaborationSpaceKeyPublication,
    val provider: CollaborationSpaceProviderState,
    val observedAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString() = "CollaborationSpaceReadinessAdmission(<metadata>)"
}
