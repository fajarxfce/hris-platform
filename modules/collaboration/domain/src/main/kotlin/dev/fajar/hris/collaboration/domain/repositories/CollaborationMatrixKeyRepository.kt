package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Fixed internal key operations; the use case owns scope and completion admission. */
interface CollaborationMatrixKeyRepository {
    /** Consumes provider one-time keys; there is no application-level replay guarantee. */
    fun claimKeys(
        request: CollaborationMatrixKeyClaim,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyClaimResult>

    /** Only explicit eligible devices; empty/all-provider-device lists are never forwarded. */
    fun deviceKeys(
        request: CollaborationMatrixKeyQuery,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyQueryResult>

    fun ownDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixDeviceKeys?>

    fun upload(
        request: CollaborationMatrixKeyUpload,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyCounts>
}
