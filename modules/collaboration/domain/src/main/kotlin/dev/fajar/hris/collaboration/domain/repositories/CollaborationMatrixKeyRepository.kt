package dev.fajar.hris.collaboration.domain.repositories

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Fixed internal own-device operations; the use case owns scope and completion admission. */
interface CollaborationMatrixKeyRepository {
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
